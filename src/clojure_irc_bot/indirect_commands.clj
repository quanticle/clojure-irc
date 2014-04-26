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

(defn youtube-links [message]
  (let [video-id (second (first (or (re-seq #"youtube.com/watch.*v=([a-zA-Z0-9\-_]*)" message) (re-seq #"youtu.be/(.*)" message))))]
    (when (not (nil? video-id))
      (let [response-map (client/get (str "https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails%2Cstatistics&id=" video-id "&key=" @google-api-key))
            json-response (json/read-str (:body response-map) :key-fn keyword)]
        (if (= (:totalResults (:pageInfo json-response)) 0)
          nil
          (str "Youtube Video: " \u0002 (:title (:snippet ((:items json-response) 0))) \u000f " (" (parse-duration (:duration (:contentDetails ((:items json-response) 0))))  " " 
            (:viewCount (:statistics ((:items json-response) 0))) " views, " (:likeCount (:statistics ((:items json-response) 0))) " likes, " 
            (:dislikeCount (:statistics ((:items json-response) 0))) " dislikes)"))))))

(defn wikilink [message]
  (let [link-texts (map second (re-seq #"\[\[([^\]]*)\]\]" message))]
    (when (not (empty? link-texts))
      (reduce #(str %1 " " %2) (map #(str "http://en.wikipedia.org/wiki/" (string/replace %1 " " "_")) link-texts)))))

(defn handle-indirect-command [socket-info my-nickname message sender dest contents]
  (let [command-responses (filter #(not (nil? %)) (map #(% contents) @indirect-command-handlers))]
    (when (not (empty? command-responses))
      (apply respond socket-info my-nickname sender dest command-responses))))

(dosync (alter indirect-command-handlers conj youtube-links))
(dosync (alter indirect-command-handlers conj wikilink))
