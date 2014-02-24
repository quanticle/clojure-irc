(ns clojure-irc-bot.direct-commands
  (:use [clojure.string :only [upper-case]]
        [clojure-irc-bot common]
        [clojure-irc-bot weather]))

(defn direct-command-type [socket-info my-nickname message sender dest contents]
  (cond 
    (.contains (upper-case contents) "PING") :ping
    (.contains (upper-case contents) "WEATHER") :weather))

(defmulti handle-direct-command direct-command-type)

(defmethod handle-direct-command :ping [socket-info my-nickname message sender dest contents]
  (respond socket-info my-nickname sender dest "pong"))

(defmethod handle-direct-command :weather [socket-info my-nickname message sender dest contents]
  )

(defmethod handle-direct-command :default [socket-info my-nickname message sender dest contents]
  (respond socket-info my-nickname sender dest "meow"))