(ns isoframe.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :showing
 (fn [db _]
   (:showing db)))

(defn sorted-todos
  [db _]
  (:todos db))

(rf/reg-sub :sorted-todos sorted-todos)

(rf/reg-sub
  :todos
  (fn [query-v _]
    (rf/subscribe [:sorted-todos]))
  (fn [sorted-todos query-v _]
    (vals sorted-todos)))

(rf/reg-sub
  :visible-todos
  (fn [query-v _]
    [(rf/subscribe [:todos])
     (rf/subscribe [:showing])])
  (fn [[todos showing] _]
    (let [filter-fn (case showing
                      :active (complement :done)
                      :done   :done
                      :all    identity)]
      (filter filter-fn todos))))

(rf/reg-sub
 :all-complete?
 :<- [:todos]
 (fn [todos _]
   (every? :done todos)))

(rf/reg-sub
 :completed-count
 :<- [:todos]
 (fn [todos _]
   (count (filter :done todos))))

(rf/reg-sub
 :footer-counts
 :<- [:todos]
 :<- [:completed-count]
 (fn [[todos completed] _]
   [(- (count todos) completed) completed]))
