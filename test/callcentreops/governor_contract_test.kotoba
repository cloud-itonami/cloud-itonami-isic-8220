(ns callcentreops.governor-contract-test
  "Integration tests: full OperationActor graph exercising the governor's
  hard checks, escalation logic, and audit trail."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [callcentreops.advisor :as advisor]
            [callcentreops.store :as store]
            [callcentreops.operation :as op]))

(defn exec-request [actor tid request ctx]
  (g/run* actor {:request request :context ctx} {:thread-id tid}))

(defn resume-approval [actor tid status]
  (g/run* actor {:approval {:status status :by "coordinator"}} {:thread-id tid :resume? true}))

(deftest call-log-full-flow
  (testing "clean call-log proposal -> auto-commit at phase 3"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-1" :phase 3}
          result (exec-request actor "t1"
                               {:op :log-call-record :campaign-id "campaign-1" :patch {:calls-handled 42}}
                               ctx)]
      (is (some? result))
      (is (> (count (store/ledger db)) 0)
          "commit must append audit facts to ledger")
      (is (> (count (store/ops-log db)) 0)
          "commit must append record to ops-log"))))

(deftest privacy-concern-always-escalates
  (testing ":flag-privacy-concern escalates for human approval, regardless of phase/confidence"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-2" :phase 3}
          result (exec-request actor "t2"
                               {:op :flag-privacy-concern :campaign-id "campaign-1"
                                :patch {:concern "unconfirmed do-not-call request" :confidence 0.99}}
                               ctx)]
      (is (some? result))
      ;; At this point the actor is paused for approval, not yet committed
      (is (= 0 (count (store/ops-log db)))
          "privacy concern must not auto-commit, must wait for approval")
      ;; Now approve it
      (resume-approval actor "t2" :approved)
      (is (> (count (store/ops-log db)) 0)
          "after approval, record must be committed"))))

(deftest unregistered-campaign-hard-hold
  (testing "unregistered campaign -> permanent HARD hold, never escalates"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-3" :phase 3}]
      (exec-request actor "t3"
                     {:op :log-call-record :campaign-id "unknown-campaign"
                      :patch {:calls-handled 1}}
                     ctx)
      (is (= 0 (count (store/ops-log db)))
          "HARD hold must never commit"))))

(deftest unverified-campaign-hard-hold
  (testing "registered but unverified campaign -> permanent HARD hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-4" :phase 3}
          result (exec-request actor "t4"
                               {:op :log-call-record :campaign-id "campaign-3"
                                :patch {:calls-handled 1}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/ops-log db)))
          "unverified campaign must HARD hold"))))

(deftest effect-not-propose-hard-hold
  (testing "proposal with :effect :commit (not :propose) -> hard hold"
    (let [db (store/seed-db)
          bad-advisor (reify advisor/Advisor
                        (-advise [_ _ req]
                          (assoc (advisor/infer nil req) :effect :commit)))
          actor (op/build db {:advisor bad-advisor})
          ctx {:actor-id "test-5" :phase 3}
          result (exec-request actor "t5"
                               {:op :log-call-record :campaign-id "campaign-1"
                                :patch {:calls-handled 1}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/ops-log db)))
          "non-:propose effect must HARD hold"))))

(deftest scope-excluded-content-hard-hold
  (testing "proposal drifting into privacy-compliance-decision scope -> permanent hard hold"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-6" :phase 3}
          result (exec-request actor "t6"
                               {:op :log-call-record :campaign-id "campaign-1"
                                :out-of-scope? true  ; triggers scope pollution in advisor
                                :patch {}}
                               ctx)]
      (is (some? result))
      (is (= 0 (count (store/ops-log db)))
          "scope-excluded content must HARD hold"))))

(deftest phase-1-approval-gate
  (testing "phase 1 approved request -> commits after human approval"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-7" :phase 1}]
      (exec-request actor "t7"
                     {:op :log-call-record :campaign-id "campaign-1"
                      :patch {:calls-handled 1}}
                     ctx)
      (is (= 0 (count (store/ops-log db)))
          "phase 1 must not auto-commit, requires approval")
      (resume-approval actor "t7" :approved)
      (is (> (count (store/ops-log db)) 0)
          "after approval, must commit")
      (is (some #(= :committed (:t %)) (store/ledger db))
          "committed fact must be logged after approval"))))

(deftest phase-2-rejected-approval-holds
  (testing "phase 2 escalated request, human REJECTS -> holds, never commits"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-7b" :phase 2}]
      (exec-request actor "t7b"
                     {:op :schedule-staffing-operation :campaign-id "campaign-2"
                      :patch {:shift "2026-08-02-night" :agents 3}}
                     ctx)
      (is (= 0 (count (store/ops-log db)))
          "phase 2 must not auto-commit, requires approval")
      (resume-approval actor "t7b" :rejected)
      (is (= 0 (count (store/ops-log db)))
          "a rejected approval must never commit")
      (is (some #(= :approval-rejected (:t %)) (store/ledger db))
          "the rejection must be logged as an immutable audit fact"))))

(deftest audit-trail-completeness
  (testing "every decision leaves immutable audit facts"
    (let [db (store/seed-db)
          actor (op/build db)
          ctx {:actor-id "test-8" :phase 3}]
      (exec-request actor "t8a"
                     {:op :log-call-record :campaign-id "campaign-1" :patch {:calls-handled 1}}
                     ctx)
      (exec-request actor "t8b"
                     {:op :log-call-record :campaign-id "unknown" :patch {:calls-handled 1}}
                     ctx)
      (let [ledger (store/ledger db)]
        (is (> (count ledger) 0))
        (is (some #(= :committed (:t %)) ledger)
            "successful commits must be logged")
        (is (some #(= :governor-hold (:t %)) ledger)
            "HARD holds must be logged")))))
