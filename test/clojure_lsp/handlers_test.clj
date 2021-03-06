(ns clojure-lsp.handlers-test
  (:require
   [clojure.test :refer :all]
   [clojure-lsp.db :as db]
   [clojure-lsp.handlers :as handlers]
   [clojure-lsp.parser :as parser]))

(deftest test-rename
  (reset! db/db {:file-envs
                 {"file://a.clj" (parser/find-references "(ns a) (def bar)" :clj)
                  "file://b.clj" (parser/find-references "(ns b (:require a)) (def x a/bar)" :clj)}})
  (testing "rename on symbol without namespace"
    (is (= "foo" (get-in (handlers/rename "file://a.clj" 1 13 "foo")
                         [:changes "file://a.clj" 0 :new-text])))
    (is (= "a/foo" (get-in (handlers/rename "file://a.clj" 1 13 "foo")
                           [:changes "file://b.clj" 0 :new-text]))))
  (testing "rename on symbol with namespace adds existing namespace"
    (is (= "foo" (get-in (handlers/rename "file://b.clj" 1 30 "foo")
                         [:changes "file://a.clj" 0 :new-text])))
    (is (= "a/foo" (get-in (handlers/rename "file://b.clj" 1 30 "foo")
                           [:changes "file://b.clj" 0 :new-text]))))
  (testing "rename on symbol with namespace removes passed-in namespace"
    (is (= "foo" (get-in (handlers/rename "file://b.clj" 1 30 "a/foo")
                         [:changes "file://a.clj" 0 :new-text])))
    (is (= "a/foo" (get-in (handlers/rename "file://b.clj" 1 30 "a/foo")
                           [:changes "file://b.clj" 0 :new-text])))))

(deftest test-completion
  (let [db-state {:file-envs
                  {"file://a.cljc" (parser/find-references
                                     (str "(ns alpaca.ns (:require [user :as alpaca]))\n"
                                          "(def barr)\n"
                                          "(def bazz)")
                                     :clj)
                   "file://b.clj" (parser/find-references
                                    (str "(ns user)\n"
                                         "(def alpha)\n"
                                         "alp\n"
                                         "ba")
                                    :clj)
                   "file://c.cljs" (parser/find-references
                                    (str "(ns alpaca.ns)\n"
                                         "(def baff)\n")
                                    :cljs)
                   "file://d.clj" (parser/find-references
                                    (str "(ns d (:require [alpaca.ns :as alpaca]))")
                                    :clj)}}]
    (testing "complete-a"
      (reset! db/db db-state)
      (is (= [{:label "alpha"}
              {:label "alpaca" :detail "user"}
              {:label "alpaca" :detail "alpaca.ns"}
              {:label "alpaca.ns" :detail "alpaca.ns"}]
             (handlers/completion "file://b.clj" 3 18))))
    (testing "complete-ba"
      (reset! db/db db-state)
			(is (= [{:label "bases"}]
             (handlers/completion "file://b.clj" 4 3))))
    (testing "complete-alph"
      (reset! db/db (update-in db-state [:file-envs "file://b.clj" 4] merge {:sym 'user/alph :str "alph"}))
      (is (= [{:label "alpha"}]
             (handlers/completion "file://b.clj" 3 3))))
    (testing "complete-alpaca"
      (reset! db/db (update-in db-state [:file-envs "file://b.clj" 4] merge {:sym 'alpaca :str "alpaca"}))
      (is (= [{:label "alpaca" :detail "user"}
              {:label "alpaca" :detail "alpaca.ns"}
              {:label "alpaca.ns" :detail "alpaca.ns"}
              {:label "alpaca/barr" :detail "alpaca.ns"}
              {:label "alpaca/bazz" :detail "alpaca.ns"}]
             (handlers/completion "file://b.clj" 3 18))))
    (testing "complete-core-stuff"
      (reset! db/db (update-in db-state [:file-envs "file://b.clj" 4] merge {:sym 'freq :str "freq"}))
      (is (= [{:label "frequencies"}]
             (handlers/completion "file://b.clj" 3 18)))
      (reset! db/db (update-in db-state [:file-envs "file://b.clj" 4] merge {:sym 'Sys :str "Sys"}))
      (is (= [{:label "System"}]
             (handlers/completion "file://b.clj" 3 18))))))

(deftest test-range-formatting
    (reset! db/db {:documents {"file://a.clj" {:text "(a  )\n(b c d)"}}})
  (is (= [{:range {:start {:line 0 :character 0}
                   :end {:line 0 :character 5}}
           :new-text "(a)"}]
         (handlers/range-formatting "file://a.clj" {:row 1 :col 1 :end-row 1 :end-col 4}))))
