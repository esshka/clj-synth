(ns cljjev.jev
  "HTTP client for TypeSafe System One (Jev)."
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s])
  (:import (java.io IOException)
           (java.net URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
                          HttpResponse HttpResponse$BodyHandlers)
           (java.time Duration)))

(def api-url "https://api.typesafe.ai/v1/systemone")

(def ^:private retry-status #{429 500 502 503 504})

(s/def ::choice string?)
(s/def ::probabilities (s/map-of string? number?))
(s/def ::score number?)

;; JSON answers keep string keys, so each field is checked on its own
(def ^:private answer-fields
  {"choice" {"choice" ::choice "probabilities" ::probabilities}
   "score" {"score" ::score}})

(defn jev-error?
  "Returns true if e was thrown by a failed API call or a malformed answer."
  [e]
  (= ::error (:type (ex-data e))))

(defn- fail [msg cause]
  (throw (ex-info msg {:type ::error} cause)))

(defn- check-answers
  "Returns answers if every question has an answer of its type's shape; throws a
  jev error naming the question and the bad field otherwise."
  [questions answers]
  (doseq [[qid q] questions
          :let [a (get answers qid)]]
    (when-not (map? a)
      (fail (str "no answer to " qid) nil))
    (doseq [[field spec] (answer-fields (:type q))
            :when (not (s/valid? spec (get a field)))]
      (fail (str "answer to " qid ", field " (pr-str field) ": "
                 (s/explain-str spec (get a field)))
            nil)))
  answers)

(defn- backoff [attempt]
  (Thread/sleep (long (* 1000 (Math/pow 2 attempt)))))

(defn client
  "Returns a policy backed by the API: (fn [state questions] answers). Network errors
  and 429/5xx responses are retried with exponential backoff; answers are checked
  against their question's type."
  [api-key & {:keys [model url timeout attempts]
              :or {model "jev-latest" url api-url timeout 30 attempts 3}}]
  (let [http (HttpClient/newHttpClient)]
    (fn [state questions]
      (let [body (json/write-str {:state state :model model :questions questions})
            request (-> (HttpRequest/newBuilder (URI. url))
                        (.timeout (Duration/ofSeconds timeout))
                        (.header "Authorization" (str "Bearer " api-key))
                        (.header "Content-Type" "application/json")
                        (.header "User-Agent" "cljjev/0.1")
                        (.POST (HttpRequest$BodyPublishers/ofString body))
                        .build)]
        (loop [attempt 0]
          (let [last? (= (inc attempt) attempts)
                result (try
                         (.send http request (HttpResponse$BodyHandlers/ofString))
                         ;; dropped connection, TLS error, timeout
                         (catch IOException e
                           (if last? (fail (str "network: " e) e) e)))]
            (if (instance? IOException result)
              (do (backoff attempt) (recur (inc attempt)))
              (let [status (.statusCode ^HttpResponse result)
                    text (str (.body ^HttpResponse result))]
                (cond
                  (< status 400)
                  (check-answers questions (get (json/read-str text) "answers"))

                  (and (retry-status status) (not last?))
                  (do (backoff attempt) (recur (inc attempt)))

                  :else
                  (fail (str "HTTP " status ": " (subs text 0 (min 500 (count text)))) nil))))))))))
