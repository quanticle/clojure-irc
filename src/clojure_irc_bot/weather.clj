(ns clojure-irc-bot.weather 
  (:use [clojure.string :only (split join)])
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defrecord Place [latitude longitude])
(def weather-cache (ref {}))
(def place-cache (ref {}))
(def wunderground-api-key (ref ""))

(defn get-place [location]
  (if (nil? (@place-cache location))
    (let [osm-data (json/read-str (:body (client/get (str "http://nominatim.openstreetmap.org/search?q=" (string/replace location #"\s" "%20") "&format=json"))) :key-fn keyword)
          place (Place. ((osm-data 0) :lat) ((osm-data 0) :lon))]
      (dosync (alter place-cache assoc location place))
      place)
    (@place-cache location)))
 
(defn extract-weather-values [api-data]
  {:name (:full (:display_location (:current_observation api-data))),
   :temp-f (:temp_f (:current_observation api-data)),
   :temp-c (:temp_c (:current_observation api-data)),
   :conditions (:weather (:current_observation api-data))})

(defn get-weather-data [lat lon]
  "Actually get the weather data from the API"
  (try
    (let [response-map (client/get (str "http://api.wunderground.com/api/" @wunderground-api-key "/conditions/q/" lat "," lon ".json"))
          response-data (json/read-str (:body response-map) :key-fn keyword)]
      (println response-data) ;;DEBUG
      (extract-weather-values response-data))
    (catch Exception e
      (println "DEBUG: " e)
      (throw (Exception. "Error retrieving weather data")))))

(defn get-weather [location]
  "Get the weather data from the cache, or, if it's not in the cache, try to look it up from the server"
  (if (or (nil? (@weather-cache location)) (> (- (System/currentTimeMillis) (:retrieval-time (@weather-cache location))) 300000))
    (let [retrieval-time (System/currentTimeMillis)
          place (get-place location)
          weather-data (get-weather-data (:latitude place) (:longitude place))]
      (dosync (alter weather-cache assoc location {:weather-data weather-data :retrieval-time retrieval-time}))
      weather-data)
    (:weather-data (@weather-cache location))))
