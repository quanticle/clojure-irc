(ns clojure-irc-bot.direct-commands
  (:use [clojure.string :only [upper-case]]
        [clojure-irc-bot common]
        [clojure-irc-bot weather]))

(def special-greeting (ref #{}))
(defn direct-command-type [socket-info my-nickname message sender dest contents]
  (cond 
    (.contains (upper-case contents) "PING") :ping
    (.contains (upper-case contents) "WEATHER") :weather))

(defmulti handle-direct-command direct-command-type)

(defmethod handle-direct-command :ping [socket-info my-nickname message sender dest contents]
  (respond socket-info my-nickname sender dest "pong"))

(defmethod handle-direct-command :weather [socket-info my-nickname message sender dest contents]
  (println "Received weather command: " contents) ;DEBUG
  (if (.startsWith contents (str ":" my-nickname))
    (let [location (second (first (re-seq #"^\S*\s*\S*\s*(.*)$" contents)))]
      (try
        (let [weather-data (get-weather location)
              weather-str (str "Weather in " (:name weather-data) ": " (:temp-f weather-data) "F/" (:temp-c weather-data) "C Conditions: " (:conditions weather-data))]
          (respond socket-info my-nickname sender dest weather-str))
        (catch Exception e
          (respond socket-info my-nickname sender dest (str "Could not get weather for " location ". Please try specifying a country-code (e.g. London, UK)")))))
    (let [location (second (first (re-seq #":\S+\s*(.*)$" contents)))]
      (try
        (let [weather-data (get-weather location)
              weather-str (str "Weather in " (:name weather-data) ": " (:temp-f weather-data) "F/" (:temp-c weather-data) "C Conditions: " (:conditions weather-data))]
          (respond socket-info my-nickname sender dest weather-str))
        (catch Exception e
          (respond socket-info my-nickname sender dest (str "Could not get weather for " location ". Please try specifying a country-code (e.g. London, UK)")))))))
      

(defmethod handle-direct-command :default [socket-info my-nickname message sender dest contents]
  (if (contains? @special-greeting (parse-nickname sender))
    (respond socket-info my-nickname sender dest "woof")
    (respond socket-info my-nickname sender dest "meow")))
