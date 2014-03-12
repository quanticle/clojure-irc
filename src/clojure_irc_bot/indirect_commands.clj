(ns clojure-irc-bot.indirect-commands
  (:use [clojure.string :only [upper-case]]
    [clojure-irc-bot common]))

(def indirect-command-handlers (ref ()))
(defn run-indirect-command [socket-info sender dest contents handler] 
  "Run an indirect command handler against the given contents, and return the results"
  (let [output ((:function handler) (re-seq (:regex handler) contents))]
    (if (not (= output ""))
      (respond socket-info sender dest output))))

(defn handle-indirect-command [socket-info handlers sender dest contents]
  (map #(run-indirect-command socket-info sender dest contents %) handlers))
