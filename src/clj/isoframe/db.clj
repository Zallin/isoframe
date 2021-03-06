(ns isoframe.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]

            [cheshire.core :as json]
            [honeysql.core :as hsql]
            [honeysql.helpers]
            [honeysql-postgres.format]
            [honeysql-postgres.helpers]))

(defn- honetize [hsql]
  (cond (map? hsql) (hsql/format hsql :quoting :ansi)
        (vector? hsql) (if (keyword? (first hsql)) (hsql/format (apply hsql/build hsql) :quoting :ansi) hsql)
        (string? hsql) [hsql]))

(defmacro from-start [start]
  `(Math/floor (/ (double (- (. java.lang.System nanoTime) ~start)) 1000000.0)))

(defn query
  ([db hsql]
   (let [sql (honetize hsql)
         start (. java.lang.System nanoTime)]
     (log/debug hsql)
     (try
       (let [res (jdbc/query db sql)]
         (log/info (str "[" (from-start start) "ms]") sql)
         res)
       (catch Exception e
         (log/error (str "[" (from-start start) "ms]") sql)
         (throw e))))))

(defn query-first [db & hsql]
  (first
   (apply query db hsql)))

(defn query-value [db & hsql]
  (when-let [row (apply query-first db hsql)]
    (first (vals row))))

(defn update-ts [{ts :ts :as rec}]
  (if ts (update rec :ts #(* (.toEpochSecond %) 1000)) rec))

(defn record-to-resource [rec]
  (when rec
    (-> (merge (:resource rec) (dissoc rec :resource))
        (update-ts))))

(defn to-table-name [rt]
  (keyword (str (name rt) "_resource")))

(defn all [db rt]
  (->> {:select [:*]
        :from [(to-table-name rt)]}
       (query db)
       (map record-to-resource)))

(defn create [db rt resource & [tx]]
  (->> {:insert-into (to-table-name rt)
        :values [{:id (hsql/raw "gen_random_uuid()::text")
                  :resource (json/generate-string resource)
                  :txid (or (:id tx) -1)}]
        :returning [:*]}
       (query-first db)
       (record-to-resource)))

(defn read [db rt id]
  (->> {:select [:*]
        :from [(to-table-name rt)]
        :where [:= :id id]}
       (query-first db)
       (record-to-resource)))

(defn update [db rt id resource & [tx]]
  (->> {:update (to-table-name rt)
        :set {:resource (json/generate-string resource)
              :txid (or (:id tx) -1)}
        :where [:= :id id]
        :returning [:*]}
       (query-first db)
       (record-to-resource)))

(defn delete [db rt id]
  (->> {:delete-from (to-table-name rt)
        :where [:= :id id]
        :returning [:*]}
       (query-first db)
       (record-to-resource)))

(defn user-by-email [db email]
  (->> {:select [:*]
        :from [:user_resource]
        :where [:= (hsql/raw "resource->>'email'") email]}
       (query-first db)
       record-to-resource))

(defn create-transaction [db desc]
  (->> {:insert-into :transaction_resource
        :values [{:resource (json/generate-string desc)}]
        :returning [:*]}
       (query-first db)
       (record-to-resource)))

(defn tasks-by-todo-id [db todo-id]
  (->> {:select [:*]
        :from [:task_resource]
        :where [:= (hsql/raw "resource->>'todo-id'") todo-id]}
       (query db)
       (map record-to-resource)))

(defn todo-bundle [db]
  (let [todos (all db :todo)
        tasks (all db :task)]
    (map (fn [{:keys [id] :as todo}]
           (let [task-idx (->> tasks
                               (filter #(= id (:todo-id %)))
                               (map #(vector (:id %) %))
                               (into {}))]
             (assoc todo :tasks task-idx)))
         todos)))
