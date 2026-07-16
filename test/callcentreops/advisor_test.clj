(ns callcentreops.advisor-test
  "Unit tests of `callcentreops.advisor` proposal generation."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [callcentreops.advisor :as adv]
            [callcentreops.store :as store]))

(def db (store/seed-db))

(deftest propose-call-log-shape
  (testing "call-log proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-call-record
                           :campaign-id "campaign-1"
                           :patch {:calls-handled 42 :avg-handle-time-sec 210}})]
      (is (= :log-call-record (:op p)))
      (is (= "campaign-1" (:campaign-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :campaign-id)))))

(deftest propose-staffing-operation-shape
  (testing "staffing-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-staffing-operation
                           :campaign-id "campaign-2"
                           :patch {:shift "2026-08-01-morning" :agents 6}})]
      (is (= :schedule-staffing-operation (:op p)))
      (is (= "campaign-2" (:campaign-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-equipment-supply-shape
  (testing "equipment-supply proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-equipment-supply
                           :campaign-id "campaign-1"
                           :patch {:item "headsets" :quantity 10}})]
      (is (= :coordinate-equipment-supply (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-privacy-concern-shape
  (testing "privacy-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-privacy-concern
                           :campaign-id "campaign-1"
                           :patch {:concern "unconfirmed do-not-call request"}})]
      (is (= :flag-privacy-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-call-record :schedule-staffing-operation
                :coordinate-equipment-supply :flag-privacy-concern]]
      (let [p (adv/infer db {:op op :campaign-id "campaign-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-call-record :schedule-staffing-operation
                :coordinate-equipment-supply :flag-privacy-concern]]
      (let [p (adv/infer db {:op op :campaign-id "campaign-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))

(deftest no-proposal-ever-drafts-a-privacy-compliance-decision
  (testing "no default (non-out-of-scope) proposal text ever claims to finalize a privacy-compliance decision"
    (doseq [op [:log-call-record :schedule-staffing-operation
                :coordinate-equipment-supply :flag-privacy-concern]]
      (let [p (adv/infer db {:op op :campaign-id "campaign-1" :patch {:concern "unconfirmed do-not-call request"}})
            blob (str/lower-case (pr-str (select-keys p [:summary :rationale :value])))]
        (is (not (str/includes? blob "gdpr")))
        (is (not (str/includes? blob "compliance decision")))
        (is (not (str/includes? blob "do-not-call override finalized")))
        (is (not (str/includes? blob "consent withdrawal resolved")))))))

(deftest out-of-scope-hook-actually-drifts-into-excluded-territory
  (testing "the test-only out-of-scope? hook produces text the governor's scope-exclusion scan WILL catch (sanity check on the hook itself)"
    (let [p (adv/infer db {:op :log-call-record :campaign-id "campaign-1"
                           :out-of-scope? true :patch {}})
          blob (str/lower-case (pr-str (select-keys p [:summary :rationale :value])))]
      (is (or (str/includes? blob "発信禁止リスト解除確定")
              (str/includes? blob "同意撤回解決確定"))
          "out-of-scope? hook must actually poison the rationale with excluded-scope content"))))
