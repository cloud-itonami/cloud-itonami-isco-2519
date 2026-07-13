(ns softdev.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [softdev.actor :as actor]
            [softdev.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-module! st {:module-id "M-1" :client-id "client-1"
                                :version [1 2 3]
                                :api-surface #{"parse" "render"}})
    st))

(deftest commits-a-clean-patch-release
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :propose-release :stake :low
                 :module-id "M-1" :new-version [1 2 4]
                 :new-surface #{"parse" "render"}}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-breaking-change-disguised-as-patch
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :propose-release :stake :low
                 :module-id "M-1" :new-version [1 2 4]
                 :new-surface #{"parse"}}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-publishes-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :publish-release :stake :medium
                 :module-id "M-1" :new-version [1 2 4]
                 :new-surface #{"parse" "render"}}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
