(ns callcentreops.dispute-referral-test
  "The support -> dispute bridge added by ADR-2607264000.

  Before this, a buyer who phoned to say \"my parcel never arrived\"
  produced a call record here and nothing at all in the marketplace, so
  the complaint died in a log. These tests cover the referral that moves
  it — and, more importantly, everything the referral is not allowed to
  become."
  (:require [callcentreops.advisor :as advisor]
            [callcentreops.governor :as governor]
            [callcentreops.phase :as phase]
            [callcentreops.store :as store]
            [clojure.test :refer [deftest is testing]]
            [marketplace.crossborder :as cb]
            [marketplace.support :as support]))

(def ctx {:actor-id "callcentre-actor" :phase 3})

(defn- db [] (store/seed-db))

(defn- campaign-id [st]
  (:campaign-id (first (store/all-campaigns st))))

(defn- patch [& {:as over}]
  (merge {:referral-id "ref-1" :ticket-id "tkt-9" :order "ord-1"
          :buyer "buyer-1" :seller "merchant.alpha"
          :reason :not-received
          :claimed-by-caller "3週間待っても届かない"
          :agent "agent-07" :agent-note "追跡番号は発行済みだが更新なし"
          :referred-at "2026-06-01T00:00:00Z"}
         over))

(defn- advise [st p]
  (advisor/-advise (advisor/mock-advisor) st
                   {:op :refer-to-dispute :campaign-id (campaign-id st) :patch p}))

(defn- check [st p]
  (governor/check {:op :refer-to-dispute :campaign-id (campaign-id st)} ctx
                  (advise st p) st))

;; ───────────────────── the bridge exists ─────────────────────

(deftest the-op-is-in-the-allowlist-and-always-escalates
  (is (contains? governor/allowed-ops :refer-to-dispute))
  (is (contains? governor/always-escalate-ops :refer-to-dispute)))

(deftest a-clean-referral-escalates-rather-than-committing
  (testing "an agent's judgement that a call is disputable is exactly the
            judgement most likely to be shaped by an upset caller"
    (let [v (check (db) (patch))]
      (is (false? (:hard? v)) (pr-str (:violations v)))
      (is (true? (:high-stakes? v)))
      (is (true? (:escalate? v)))
      (is (false? (:ok? v)) "no confidence value makes this automatic"))))

(deftest it-is-never-auto-eligible-at-any-phase
  (doseq [[p {:keys [auto label]}] phase/phases]
    (is (not (contains? auto :refer-to-dispute))
        (str "phase " p " (" label ")")))
  (testing "but phase 3 does permit writing it"
    (is (contains? (:writes (get phase/phases 3)) :refer-to-dispute))
    (is (= :escalate (:disposition (phase/gate 3 {:op :refer-to-dispute} :commit))))))

;; ───────────────── what a referral may never become ─────────────────

(deftest a-referral-carrying-a-verdict-is-a-HARD-block
  (testing "a support agent is the person most likely to form a view about
            fault, the least equipped to be held to it, and the most
            trusted by the caller"
    (doseq [k [:referral/outcome :referral/fault :referral/liable :fault :outcome]]
      (let [st (db)
            p (advise st (patch))
            tampered (assoc-in p [:value :referral k] :seller)
            v (governor/check {:op :refer-to-dispute} ctx tampered st)]
        (is (true? (:hard? v)) (str k))
        (is (some #{:referral-carries-a-verdict} (mapv :rule (:violations v))) (str k))))))

(deftest an-adjudicated-flag-is-a-HARD-block
  (let [st (db)
        p (advise st (patch))
        tampered (assoc-in p [:value :referral :referral/adjudicated?] true)
        v (governor/check {:op :refer-to-dispute} ctx tampered st)]
    (is (true? (:hard? v)))
    (is (some #{:referral-carries-a-verdict} (mapv :rule (:violations v))))))

(deftest an-untraceable-referral-is-a-HARD-block
  (testing "a complaint with no contact and no named agent is not a
            referral, it is a rumour"
    (doseq [[over expected] [[{:ticket-id ""} :missing-ticket]
                             [{:agent ""} :missing-agent]
                             [{:order ""} :missing-order]]]
      (let [v (check (db) (merge (patch) over))]
        (is (true? (:hard? v)) (pr-str over))
        (is (some #{expected} (mapv :rule (:violations v))) (pr-str over))))))

(deftest the-reason-must-be-a-real-dispute-reason
  (testing "a referral cannot invent a category dispute reporting has no
            bucket for"
    (let [v (check (db) (patch :reason :agent-thinks-seller-is-dodgy))]
      (is (true? (:hard? v)))
      (is (some #{:unknown-reason} (mapv :rule (:violations v)))))))

(deftest a-missing-referral-draft-is-refused
  (let [st (db)
        v (governor/check {:op :refer-to-dispute} ctx
                          {:op :refer-to-dispute :effect :propose :confidence 0.9
                           :value {}}
                          st)]
    (is (true? (:hard? v)))
    (is (some #{:referral-missing} (mapv :rule (:violations v))))))

;; ───────────────── the hand-off itself ─────────────────

(deftest the-referral-becomes-an-ordinary-non-adjudicating-dispute
  (let [st (db)
        r (get-in (advise st (patch)) [:value :referral])
        d (support/open-with-evidence r)]
    (is (empty? (cb/dispute-errors d)))
    (is (= :opened (:dispute/state d)))
    (is (= :not-received (:dispute/reason d)))
    (is (false? (:dispute/adjudicated-by-actor? d)))
    (testing "and it records where it came from, so a reviewer can pull the call"
      (is (= :support-referral (:dispute/source d)))
      (is (= "tkt-9" (:dispute/ticket d))))
    (testing "the support contact is filed as the BUYER's evidence"
      (let [[e] (:dispute/evidence d)]
        (is (= :buyer (:evidence/party e)))
        (is (= :support-contact (:evidence/kind e)))
        (is (= "tkt-9" (:evidence/ref e)))))))

(deftest caller-claim-and-agent-note-stay-attributed-and-separate
  (let [st (db)
        r (get-in (advise st (patch)) [:value :referral])
        n (:dispute/narrative (support/->dispute r))]
    (is (re-find #"\[caller\] 3週間待っても届かない" n))
    (is (re-find #"\[agent agent-07\]" n))))

(deftest nobody-in-this-path-decides-anything
  (testing "the fleet invariant is unchanged — the bridge only moves a
            complaint from where it was heard to where it can be worked"
    (let [st (db)
          r (get-in (advise st (patch)) [:value :referral])
          d (support/open-with-evidence r)]
      (is (nil? (:dispute/decision d)))
      (is (nil? (cb/record-decision d {:outcome :buyer-favoured :decided-by "agent-07"}))
          "not even under review yet")
      (let [under (cb/advance-dispute d :under-review)]
        (is (nil? (cb/record-decision under {:outcome :buyer-favoured :decided-by ""})))
        (is (some? (cb/record-decision under {:outcome :buyer-favoured
                                              :decided-by "ops-01" :decided-at "t"})))))))

;; ───────────────── the existing actor is untouched ─────────────────

(deftest the-original-four-ops-are-unchanged
  (testing "this was an additive change; nothing that worked before may
            have started failing"
    (doseq [op [:log-call-record :schedule-staffing-operation
                :coordinate-equipment-supply :flag-privacy-concern]]
      (is (contains? governor/allowed-ops op) (str op)))
    (is (contains? governor/always-escalate-ops :flag-privacy-concern))
    (is (= #{:log-call-record :schedule-staffing-operation :coordinate-equipment-supply}
           (:auto (get phase/phases 3)))
        "the phase-3 auto set is exactly what it was")))

(deftest referral-proposals-never-self-trip-scope-exclusion
  (testing "a referral necessarily talks about complaints and orders"
    (let [v (check (db) (patch))]
      (is (not-any? #{:scope-excluded} (mapv :rule (:violations v)))))))
