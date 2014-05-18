(ns clojure-irc-bot.indirect-commands
  (:use [clojure-irc-bot common])
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.string :as string])
  (:import [org.joda.time.format ISOPeriodFormat]))

(def indirect-command-handlers (ref ()))
(def google-api-key (ref ""))

(defn parse-duration [duration-string]
  (let [timeFormatter (ISOPeriodFormat/standard)
        video-length (.parsePeriod timeFormatter duration-string)]
    (str (format "%02d" (.getHours video-length)) ":" (format "%02d" (.getMinutes video-length)) ":" (format "%02d" (.getSeconds video-length)))))

(defn get-video-data [video-id]
  (let [response-map (client/get (str "https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails%2Cstatistics&id=" video-id "&key=" @google-api-key))
        json-response (json/read-str (:body response-map) :key-fn keyword)]
    (if (= (:totalResults (:pageInfo json-response)) 0)
      nil
      {:title (:title (:snippet ((:items json-response) 0)))
       :duration (parse-duration (:duration (:contentDetails ((:items json-response) 0))))
       :views (:viewCount (:statistics ((:items json-response) 0)))
       :likes (:likeCount (:statistics ((:items json-response) 0)))
       :dislikes (:dislikeCount (:statistics ((:items json-response) 0)))})))

(defn extract-video-ids [message]
  (let [long-url-ids (map second (re-seq #"youtube.com/watch[^ ]*v=([a-zA-Z0-9\-_]+)" message))
        short-url-ids (map second (re-seq #"youtu.be/([a-zA-Z0-9\-_]+)" message))]
    (into long-url-ids short-url-ids)))

(defn youtube-links [message]
  (let [video-ids (extract-video-ids message)
        video-data (map get-video-data video-ids)]
    (if (not (empty? video-data))
      (map #(str "YouTube Video: " \u0002 (:title %1) \u000f " (Duration: " (:duration %1) " " (:views %1) " views " (:likes %1) " likes " (:dislikes %1) " dislikes)") video-data)
      nil)))

(defn wikilink [message]
  (let [link-texts (map second (re-seq #"\[\[([^\]]*)\]\]" message))]
    (when (not (empty? link-texts))
      (doall (map #(str "http://en.wikipedia.org/wiki/" (string/replace %1 " " "_")) link-texts)))))

(defn handle-indirect-command [socket-info my-nickname message sender dest contents]
  (let [command-responses (flatten (filter #(not (nil? %)) (map #(% contents) @indirect-command-handlers)))]
    (when (not (empty? command-responses))
      (doall (map #(respond socket-info my-nickname sender dest %) command-responses)))))

(dosync (alter indirect-command-handlers conj youtube-links))
(dosync (alter indirect-command-handlers conj wikilink))
