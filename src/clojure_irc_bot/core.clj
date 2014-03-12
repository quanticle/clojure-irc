(ns clojure-irc-bot.core
  (:gen-class)
  (:use [clojure.string :only [upper-case]]
    [clojure-irc-bot common direct-commands indirect-commands weather])
  (:import [java.net Socket]
    [java.io InputStreamReader BufferedReader OutputStreamWriter PrintWriter 
      FileReader]
    [org.ini4j Wini]))

(def nickname (ref ""))
(defn privmsg-type [socket-info my-nickname message sender dest contents]
    (if (or (= dest my-nickname) (.startsWith contents (str ":" my-nickname)))
      :direct-command
      :indirect-command))

(defmulti handle-privmsg privmsg-type)

(defmethod handle-privmsg :direct-command [socket-info my-nickname message sender dest contents]
  (handle-direct-command socket-info my-nickname message sender dest contents))

(defmethod handle-privmsg :indirect-command [socket-info my-nickname message sender dest contents]
  (println "Received indirect command")
  (handle-indirect-command socket-info my-nickname message sender dest contents))

(defmethod handle-privmsg :default [socket-info message sender dest contents]
    (println (str "Received PRIVMSG"))
    (println (str "Sender: " sender))
    (println (str "Dest: " dest))
    (println (str "Contents: " contents)))

(defn message-type [socket-info message]
    "Gets the message type. I'm handling PING as a dirty special case because 
    hybrid-ircd doesn't put a sender on PING, which screws up the regex."
    (if (.startsWith message "PING")
      "PING"
      (let [message-type (first (rest (re-seq #"[\S]+" message)))]
        message-type)))

(defmulti handle-message message-type)

(defmethod handle-message "PING" [socket-info ping-message]
    "Returns a pong message with the data from the ping message"
    (println (str "Received PING: " ping-message)) ;DEBUG
    (let [data-start-index (.indexOf ping-message ":")
          data (.substring ping-message (inc data-start-index))]
        (.print (:toServer socket-info) (str "PONG :" data "\r\n")) 
        (.flush (:toServer socket-info))))

(defmethod handle-message "PRIVMSG" [socket-info message]
    "Passes actual messages (as opposed to housekeeping stuff like PING to the
    subsytem responsible for dealing with these"
    (handle-privmsg socket-info @nickname message (get-privmsg-sender message) 
      (get-privmsg-dest message)
      (get-privmsg-contents message)))

(defmethod handle-message :default [socket-info message]
    (println message))

(defn event-loop [socket-info]
    "Infinite loop that reads messages from the server and responds to them"
    (loop [message (.readLine (:fromServer socket-info))]
        (handle-message socket-info message)
        (recur (.readLine (:fromServer socket-info)))))

(defn -main
  "I am an IRCBot"
  [& args]
  (let [bot-config (read-config "botconfig.ini")
        socket-info (connect-to-server (:server bot-config) 6667)]
    (println "Opened socket") ;DEBUG
    (dosync (ref-set nickname (:nickname bot-config)))
    (dosync (ref-set wunderground-api-key (:wunderground-api-key bot-config)))
    (dosync (ref-set google-api-key (:google-api-key bot-config)))
    (dosync (alter special-greeting into (:special-greeting-nicks bot-config)))
    (send-nickname socket-info nickname)    
    (set-username socket-info (:username bot-config))
    (join-channel socket-info (:channel bot-config))
    (event-loop socket-info)))
