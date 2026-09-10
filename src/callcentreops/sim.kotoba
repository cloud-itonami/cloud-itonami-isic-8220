(ns callcentreops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean call-record logging
  request through intake -> advise -> govern -> decide -> approval ->
  commit at phase 1 (assisted-logging, always approval), then re-runs
  the same op at phase 3 (supervised-auto, clean + high confidence ->
  auto-commit), then a staffing-operation-scheduling request,
  equipment-supply coordination (both auto-commit clean at phase 3),
  then a privacy-concern flag (ALWAYS escalates, at any phase --
  approve, then commit), then HARD-hold scenarios: an unregistered
  campaign, a campaign registered but not yet verified, a proposal
  whose own `:effect` is not `:propose`, and a proposal that has
  drifted into the permanently-excluded data-privacy-compliance-
  decision scope."
  (:require [langgraph.graph :as g]
            [callcentreops.advisor :as advisor]
            [callcentreops.store :as store]
            [callcentreops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "campaign-supervisor-1"}} {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "campaign-supervisor-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        supervisor-phase-1 {:actor-id "sup-1" :actor-role :campaign-supervisor :phase 1}
        supervisor-phase-2 {:actor-id "sup-1" :actor-role :campaign-supervisor :phase 2}
        supervisor-phase-3 {:actor-id "sup-1" :actor-role :campaign-supervisor :phase 3}
        actor (op/build db)]

    (println "== log-call-record campaign-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-call-record :campaign-id "campaign-1"
                                  :patch {:calls-handled 42 :avg-handle-time-sec 210 :outcome "resolved"}} supervisor-phase-1)]
      (println r)
      (println "-- human campaign supervisor approves --")
      (println (approve! actor "t1")))

    (println "\n== schedule-staffing-operation campaign-2 (phase 2, escalates -- human REJECTS) ==")
    (let [r (exec-op actor "t1b" {:op :schedule-staffing-operation :campaign-id "campaign-2"
                                   :patch {:shift "2026-08-02-night" :agents 3}} supervisor-phase-2)]
      (println r)
      (println "-- human campaign supervisor rejects --")
      (println (reject! actor "t1b")))

    (println "\n== log-call-record campaign-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-call-record :campaign-id "campaign-1"
                                  :patch {:calls-handled 51 :avg-handle-time-sec 195}} supervisor-phase-3))

    (println "\n== schedule-staffing-operation campaign-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-staffing-operation :campaign-id "campaign-1"
                                  :patch {:shift "2026-08-01-morning" :agents 6}} supervisor-phase-3))

    (println "\n== coordinate-equipment-supply campaign-2 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-equipment-supply :campaign-id "campaign-2"
                                  :patch {:item "headsets" :quantity 10}} supervisor-phase-3))

    (println "\n== flag-privacy-concern campaign-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :flag-privacy-concern :campaign-id "campaign-1"
                                 :patch {:concern "unconfirmed do-not-call request received via phone" :confidence 0.9}} supervisor-phase-3)]
      (println r)
      (println "-- human campaign supervisor reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== log-call-record campaign-99 (unregistered campaign -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :log-call-record :campaign-id "campaign-99"
                                  :patch {:calls-handled 1}} supervisor-phase-3))

    (println "\n== log-call-record campaign-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-call-record :campaign-id "campaign-3"
                                  :patch {:calls-handled 1}} supervisor-phase-3))

    (println "\n== schedule-staffing-operation campaign-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t8" {:op :schedule-staffing-operation :campaign-id "campaign-1"
                                           :patch {:shift "reprint"}} supervisor-phase-3)))

    (println "\n== log-call-record campaign-1, advisor drifts into privacy-compliance-decision scope -> HARD hold, permanent ==")
    (println (exec-op actor "t9" {:op :log-call-record :campaign-id "campaign-1"
                                   :out-of-scope? true
                                   :patch {}} supervisor-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed operations log ==")
    (doseq [r (store/ops-log db)] (println r))))
