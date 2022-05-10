(ns podcasts
  (:use [clojure.repl])
  (:require

   [clojure.xml]

   [clj-time.core :as t]
   [clj-time.format :as f]

   [postal.core] ; email

   [hiccup.core] ; html 
   [hiccup.table]))

(def ndays
 "look for episodes younger than this"
  7)

(defn parse-feed
  "read xml into tree map"
  [feed]
  (println "parse-feed called")
  (xml-seq (clojure.xml/parse feed)))


(defn get-episodes [feed]
  (->>
   feed
   (filter #(= :title (:tag %)))
   (map #(first (:content %)))
   (rest)  ; remove a header record
   )
  )


(defn days-ago
  "compute num days since published"
  [x]
  (t/in-days (t/interval
              (f/parse (f/formatter :rfc822) x)
              (t/now))))

(defn custom-format-date [x]
  "format date to my visual liking"
  (f/unparse
   (f/formatter "EEE, dd MMM yy")
   (f/parse (f/formatter :rfc822) x
          ;; "Wed, 17 Jun 2020 12:00:00 +0000"
            )))

(defn get-dates [feed]
  "return the date and num days ago"
  (->>
   feed
   (filter #(= :pubDate (:tag %)))
   (map #(first (:content %)))
   (map #(cons (custom-format-date %) (list (days-ago %))))
   ))


(def get-feeds
  "return a list of podcast urls"
  (->>
   (slurp "src/feeds.txt")
   (clojure.string/split-lines)))

(defn get-recent-rss
 "return a list of recent episodes for a given podcast url" 
  [feed_url]

(let [feed (parse-feed feed_url)
      titles (get-episodes feed)
      feed-title (first titles)
      ]
  (->>
   (get-dates feed)
   (map cons titles) 
   (map cons (repeat feed-title)) ;; add podcast title
   (rest)  ;; remove header record
   (filter #(< (last %) ndays))
   )
   )
)

(def get-recent-podcasts
  "stack recent episodes"
  (->> get-feeds
       (map #(get-recent-rss %))
       (apply concat)))


(def format-table
  (clojure.string/replace
   "<style>
table {
  font-family: arial, sans-serif;
  border-collapse: collapse;
  width: 100%;
}

td, th {
  border: 1px solid #dddddd;
  text-align: left;
  padding: 8px;
}

tr:nth-child(even) {
  background-color: #dddddd;
}
</style>
", #"\n" ""))

(def make-html
  "make html table for emailing"
  (hiccup.core/html
   (hiccup.table/to-table1d
    (map vec get-recent-podcasts)
    [1 "Episode" 2 "Date" 3 "Days Ago" 0 "Podcast"])))

(defn run [args]
  (println 
  (postal.core/send-message {:host "smtp.gmail.com"
                             :user (System/getenv "EMAIL")
                             :pass (System/getenv "EMAILAPPPWD")
                             :port 587
                             :tls true}
                            {:from (System/getenv "EMAIL")
                             :to (System/getenv "EMAIL")
                             :subject "Recent Podcasts"
                             :body [{:type "text/html" :content 
                                     (str format-table 
                                          make-html
                                          )
                                     }]}) 
  )
  )
