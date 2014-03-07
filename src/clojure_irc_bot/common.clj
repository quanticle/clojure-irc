(ns clojure-irc-bot.common
  (:gen-class)
  (:use [clojure.string :only [upper-case]])
  (:import [java.net Socket]
    [java.io InputStreamReader BufferedReader OutputStreamWriter PrintWriter 
      FileReader]
    [org.ini4j Wini]))

(defn read-config [config-file-name]
    (let [ini-object  (Wini. (BufferedReader. (FileReader. config-file-name)))]
          {:server (.get ini-object "botconfig" "server")
           :username (.get ini-object "botconfig" "username")
           :nickname (.get ini-object "botconfig" "nickname")
           :channel (.get ini-object "botconfig" "channel")
           :wunderground-api-key (.get ini-object "botconfig" "wunderground-api-key")}))

(defn connect-to-server [server port]
    "Connect to a server and return a data structure with the socket, input stream and output stream
    The data structure is a map of the form:
      {:socket <socket object>,
       :fromServer <BufferedReader object wrapping the socket's input stream>
       :toServer <Printwriter object wrapping the socket's output stream>}
    "
    (let [ircSocket (Socket. server port)
          socketInput (BufferedReader. (InputStreamReader. (.getInputStream ircSocket)))
          socketOutput (PrintWriter. (OutputStreamWriter. (.getOutputStream ircSocket)))]
          {:socket ircSocket,
           :fromServer socketInput,
           :toServer socketOutput}))

(defn set-username [socket-info username]
    "Sends the USER message to the server"
    (println (str "Sending: " "USER " username " " "0 " "* " ":clojure-irc-bot")) ;DEBUG
    (.print (:toServer socket-info) (str "USER " username " " "0 " "* " ":clojure-irc-bot\r\n"))
    (.flush (:toServer socket-info)))

(defn send-nickname [socket-info nickname]
    "Sets the nickname of the bot"
    (println (str "Sending: " "NICK " @nickname)) ;DEBUG
    (.print (:toServer socket-info) (str "NICK " @nickname "\r\n"))
    (.flush (:toServer socket-info)))

(defn join-channel [socket-info channel]
    "Joins a channel."
    (println (str "Sending: " "JOIN :" channel))
    (.print (:toServer socket-info) (str "JOIN :" channel "\r\n"))
    (.flush (:toServer socket-info)))

(defn parse-nickname [sender-string]
  (second (first (re-seq #"^:([\S]+)!" sender-string))))

(defn respond [socket-info my-nickname sender dest response]
  "Respond to a message. Responses to a channel are prefixed with the asking 
  username, so the user knows that they're being responded to."
  (if (= dest my-nickname)
    (do 
      (println "Sending: " (str "PRIVMSG " (parse-nickname sender) " :" response)) ;DEBUG
      (.print (:toServer socket-info) (str "PRIVMSG " (parse-nickname sender) " :" response "\r\n"))
      (.flush (:toServer socket-info)))
    (do
      (println "Responding to channel " dest) ;DEBUG
      (println "Sending: " (str "PRIVMSG " dest " :\"" (parse-nickname sender) ": " response) "\"") ;DEBUG
      (.print (:toServer socket-info) (str "PRIVMSG " dest " :" (parse-nickname sender) ": " response "\r\n"))
      (.flush (:toServer socket-info)))))

(defn get-privmsg-sender [message]
  "Gets the sender of a message"
  (first (re-seq #"^:\S+" message)))

(defn get-privmsg-dest [message]
  "Gets the destination of a message"
  (nth (first (re-seq #"PRIVMSG ([\S]+)" message)) 1))

(defn get-privmsg-contents [message]
  "Gets the contents of a message"
  (nth (first (re-seq #"PRIVMSG [\S]+ (.*)" message)) 1))

