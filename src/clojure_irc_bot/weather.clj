(ns clojure-irc-bot.weather 
  (:use [clojure.string :only (split join)])
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defrecord Place [city-name country-code population latitude longitude])
(def weather-cache (ref {}))
(def places (ref #{}))
(def wunderground-api-key (ref ""))

(defn load-places [place-file]
  (with-open [in-file (io/reader place-file)]
    (let [csv-lines (csv/read-csv in-file :separator \tab :quote \u0000)
          place-records (map #(Place. (% 2) (% 8) (Integer/parseInt (% 14)) (% 4) (% 5)) csv-lines)] 
      (dosync (alter places into place-records)))))

(defn get-place 
  ([place-name]
    (if (empty? @places)
      (load-places "cities5000.txt"))
    (first (sort-by :population > (filter #(.contains (.toUpperCase (:city-name %)) (.toUpperCase place-name)) @places))))
  ([place-name country-code]
    (if (empty? @places)
      (load-places "cities5000.txt"))
    (if (= (.toUpperCase country-code) "UK")
      (first (sort-by :population > 
        (filter #(and (.contains (.toUpperCase (:city-name %)) (.toUpperCase place-name)) (= (:country-code %) "GB")) @places)))
      (first (sort-by :population > 
        (filter #(and (.contains (.toUpperCase (:city-name %)) (.toUpperCase place-name)) (= (:country-code %) (.toUpperCase country-code))) 
          @places))))))

(defn extract-weather-values [api-data]
  {:city-name (:city (:display_location (:current_observation api-data))),
   :country-name (:country (:display_location (:current_observation api-data))),
   :temp-f (:temp_f (:current_observation api-data)),
   :temp-c (:temp_c (:current_observation api-data)),
   :conditions (:weather (:current_observation api-data))})

(defn get-weather-data [lat lon]
  "Actually get the weather data from the API"
  (try
    (let [response-map (client/get (str "http://api.wunderground.com/api/" @wunderground-api-key "/conditions/q/" lat "," lon ".json"))
          response-data (json/read-str (:body response-map) :key-fn keyword)]
      (extract-weather-values response-data))
    (catch Exception e
      (println "DEBUG: " e)
      (throw (Exception. "Error retrieving weather data")))))

(defn get-weather [location]
  "Get the weather data from the cache, or, if it's not in the cache, try to look it up from the server"
  (if (and (@weather-cache location) (< (- (System/currentTimeMillis) (:retrieval-time (@weather-cache location))) 300000))
    (:weather-data (@weather-cache location))
    (let [[_ city country-code] (first (re-seq #"^([^,]+),?[^A-Za-z]*(.*)" location))
          current-time (System/currentTimeMillis)]
      (if (empty? country-code)
        (let [place (get-place city)]
          (if (nil? place)
            (throw (Exception. "Could not find city"))
            (let [weather-data (get-weather-data (:latitude place) (:longitude place))]
              (dosync (alter weather-cache assoc location {:weather-data weather-data :retrieval-time current-time}))
              weather-data)))
        (let [place (get-place city country-code)]
          (if (nil? place)
            (throw (Exception. "Could not find city"))
            (let [weather-data (get-weather-data (:latitude place) (:longitude place))]
              (dosync (alter weather-cache assoc location {:weather-data weather-data :retrieval-time current-time}))
              (println "Weather data: " weather-data) ;;DEBUG
              weather-data)))))))
