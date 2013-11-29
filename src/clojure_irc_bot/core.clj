(ns clojure-irc-bot.core
  (:gen-class)
  (:import [java.net Socket]
           [java.io InputStreamReader BufferedReader OutputStreamWriter PrintWriter]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(defn connect-to-server [server port]
    "Connect to a server and return a data structure with the socket, input stream and output stream
    The data structure is a map of the form:
      {:socket <socket object>,
       :fromServer <BufferedReader object wrapping the socket's input stream>
       :toServer <Printwriter object wrapping the socket's output stream>}
    "
    (let [ircSocket (Socket. server port)
          socketInput (BufferedReader. (InputStreamReader. (.getInputStream socket)))
          socketOutput (PrintWriter. (OutputStreamWriter. (.getOutputStream socket)))]
          {:socket ircSocket,
           :fromServer socketInput,
           :toServer socketOutput}))

(defn init-connection [socket-info username]
    "Sends the USER message to the server, setting up the connection for future work"
    (.print (:toServer socket-info) (str "USER " username " " "0 " "* " ":clojure-irc-bot"))
    (.flush (:toServer socket-info)))

(defn set-nickname [socket-info nickname]
    "Sets the nickname of the bot"
    (.print (:toServer socket-info) (str "NICK " nickname))
    (.flush (:toServer socket-info)))

(defn message-type [socket-info message]
    (let [message-type (first (re-seq #"[A-Za-Z0-9]+" message))]
        message-type))

(defmulti handle-message message-type)

(defmethod handle-message "PING" [socket-info ping-message]
    "Returns a pong message with the data from the ping message"
    (let [data-start-index (.indexOf ping-message ":")
          data (.substring ping-message (inc data-start-index))]
        (.print (:toServer socket-info) (str "PONG :" data))))

(defmethod handle-message :default [socket-info message]
    (println message))

(defn event-loop [socket-info]
    "Infinite loop that reads messages from the server and responds to them"
    (loop [message (.readLine (:fromServer socket-info))]
        (handle-message socket-info message)
        (recur (.readLine (fromServer socket-info)))))