(ns clojure-irc-bot.weather 
  (:use [clojure.string :only (split join)])
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

(def weather-cache (ref {}))

(defn k-to-f [kelvin-temp]
    (+ (* (/ 9 5) (- kelvin-temp 273)) 32))

(defn k-to-c [kelvin-temp]
    (- kelvin-temp 273))

(defn get-weather-data [location]
  "Actually get the weather data from the API"
  (let [location-query (join "," (split location #"[^a-zA-Z]"))
        response-map (client/get (str "http://api.openweathermap.org/data/2.5/weather?q=" location-query))
        response-data (json/read-str (:body response-map) :key-fn keyword)]
    {:city-name (:name response-data),
     :country-name (:country (:sys response-data)),
     :temp-f (k-to-f (:temp (:main response-data))),
     :temp-c (k-to-c (:temp (:main response-data))),
     :conditions (:main (first (:weather response-data)))}))

(defn get-weather [location]
  "Get the weather data from the cache, or, if it's not in the cache, try to look it up from the server"
  (if (and (@weather-cache location) (< (- (System/currentTimeMillis) (:retrieval-time (@weather-cache location))) 300000))
    ;; If the data is in the cache and the cache entry isn't stale, return the data from the cache
    (:weather-data (@weather-cache location))
    ;; Otherwise, get the data, add it to the cache and return it
    (do
      (let [weather-data (get-weather-data location)
            current-time (System/currentTimeMillis)]
        (dosync (alter weather-cache assoc location {:weather-data weather-data :retrieval-time current-time}))
        weather-data))))

