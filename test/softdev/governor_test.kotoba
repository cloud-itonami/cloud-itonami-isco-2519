(ns softdev.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [softdev.store :as store]
            [softdev.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-module! st {:module-id "M-1" :client-id "client-1"
                                :version [1 2 3]
                                :api-surface #{"parse" "render" "validate"}})
    st))

(defn- release [version surface]
  {:op :propose-release :effect :propose :module-id "M-1"
   :new-version version :new-surface surface
   :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-patch-release-same-surface
  (let [st (fresh-store)
        v (governor/check req {} (release [1 2 4]
                                          #{"parse" "render" "validate"}) st)]
    (is (:ok? v))))

(deftest ok-minor-release-with-addition
  (let [st (fresh-store)
        v (governor/check req {} (release [1 3 0]
                                          #{"parse" "render" "validate" "lint"}) st)]
    (is (:ok? v))))

(deftest ok-major-release-with-removal
  (let [st (fresh-store)
        v (governor/check req {} (release [2 0 0]
                                          #{"parse" "render"}) st)]
    (is (:ok? v))))

(deftest hard-on-breaking-change-without-major
  (testing "semver is a contract computed from the diff, not a mood"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (release [1 2 4]
                                                   #{"parse" "render"})
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :breaking-without-major (:rule %)) (:violations v))))))

(deftest hard-on-addition-without-minor
  (testing "a surface addition shipped as a patch is an arithmetic violation"
    (let [st (fresh-store)
          v (governor/check req {} (release [1 2 4]
                                            #{"parse" "render" "validate" "lint"}) st)]
      (is (:hard? v))
      (is (some #(= :addition-without-minor (:rule %)) (:violations v))))))

(deftest hard-on-version-not-advanced
  (testing "re-release and rollback are arithmetic violations"
    (let [st (fresh-store)]
      (doseq [ver [[1 2 3] [1 2 2] [0 9 9]]]
        (let [v (governor/check req {} (release ver
                                                #{"parse" "render" "validate"}) st)]
          (is (:hard? v))
          (is (some #(= :version-not-advanced (:rule %)) (:violations v))))))))

(deftest hard-on-unknown-module
  (let [st (fresh-store)
        v (governor/check req {} (assoc (release [1 2 4]
                                                 #{"parse" "render" "validate"})
                                        :module-id "M-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-module (:rule %)) (:violations v)))))

(deftest hard-on-foreign-module
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {}
                            (release [1 2 4] #{"parse" "render" "validate"}) st)]
      (is (:hard? v))
      (is (some #(= :module-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {}
                          (release [1 2 4] #{"parse" "render" "validate"}) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (release [1 2 4]
                                                 #{"parse" "render" "validate"})
                                        :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-registry-publication
  (let [st (fresh-store)
        v (governor/check req {} (assoc (release [1 2 4]
                                                 #{"parse" "render" "validate"})
                                        :op :publish-release
                                        :stake :medium) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (release [1 2 4]
                                                 #{"parse" "render" "validate"})
                                        :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
