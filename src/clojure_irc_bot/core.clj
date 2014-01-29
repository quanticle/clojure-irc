(ns clojure-irc-bot.core
  (:gen-class)
  (:import [java.net Socket]
           [java.io InputStreamReader BufferedReader OutputStreamWriter 
               PrintWriter FileReader]
           [org.ini4j Wini]))

(def nickname (atom ""))

(defn read-config [config-file-name]
    (let [ini-object  (Wini. (BufferedReader. (FileReader. config-file-name)))]
          {:server (.get ini-object "botconfig" "server")
           :username (.get ini-object "botconfig" "username")
           :nickname (.get ini-object "botconfig" "nickname")
           :channel (.get ini-object "botconfig" "channel")}))

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

(defn respond-to-direct-command [socket-info sender dest response]
  (if (= dest @nickname)
    (do 
      (println "Sending: " (str "PRIVMSG " (parse-nickname sender) " :" response)) ;DEBUG
      (.print (:toServer socket-info) (str "PRIVMSG " (parse-nickname sender) " :" response "\r\n"))
      (.flush (:toServer socket-info)))
    (do
      (println "Responding to channel " dest) ;DEBUG
      (println "Sending: " (str "PRIVMSG " dest " :\"" (parse-nickname sender) ": " response) "\"") ;DEBUG
      (.print (:toServer socket-info) (str "PRIVMSG " dest " :" (parse-nickname sender) ": " response "\r\n"))
      (.flush (:toServer socket-info)))))

(defn direct-command-type [socket-info message sender dest contents]
  (cond 
    (.contains (.toUpperCase contents) "PING") :ping
    (.contains (.toUpperCase contents) "WEATHER") :weather))

(defmulti handle-direct-command direct-command-type)

(defmethod handle-direct-command :ping [socket-info message sender dest contents]
  (respond-to-direct-command socket-info sender dest "pong"))

(defmethod handle-direct-command :default [socket-info message sender dest contents]
  (println "Received unrecognized direct command") ;DEBUG
  (println "Sender: " sender) ;DEBUG
  (println "Dest:" dest) ;DEBUG
  (println "Contents:" contents) ;DEBUG
  (respond-to-direct-command socket-info sender dest "meow"))

(defn get-privmsg-sender [message]
  "Gets the sender of a message"
  (first (re-seq #"^:\S+" message)))

(defn get-privmsg-dest [message]
  "Gets the destination of a message"
  (nth (first (re-seq #"PRIVMSG ([\S]+)" message)) 1))

(defn get-privmsg-contents [message]
  "Gets the contents of a message"
  (nth (first (re-seq #"PRIVMSG [\S]+ (.*)" message)) 1))

(defn privmsg-type [socket-info message sender dest contents]
    (if (or (= dest @nickname) (.startsWith contents (str ":" @nickname)))
      :direct-command
      :indirect-command))

(defmulti handle-privmsg privmsg-type)

(defmethod handle-privmsg :direct-command [socket-info message sender dest contents]
  (handle-direct-command socket-info message sender dest contents))

(defmethod handle-privmsg :indirect-command [socket-info message sender dest contents]
  (println "Received indirect command")
  (println message)
  (println sender)
  (println dest)
  (println contents))

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
    (handle-privmsg socket-info message (get-privmsg-sender message) 
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
    (swap! nickname str (:nickname bot-config))
    (send-nickname socket-info nickname)    
    (set-username socket-info (:username bot-config))
    (join-channel socket-info (:channel bot-config))
    (event-loop socket-info)))