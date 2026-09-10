(ns callcentreops.governor-test
  "Pure unit tests of `callcentreops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [callcentreops.governor :as gov]
            [callcentreops.advisor :as advisor]
            [callcentreops.store :as store]))

(def campaign-1 {:campaign-id "campaign-1" :client "Acme Retail Support Line" :registered? true :verified? true})
(def campaign-3 {:campaign-id "campaign-3" :client "Riverside Collections" :registered? true :verified? false})

(defn- clean-proposal [op campaign-id]
  {:op op :campaign-id campaign-id :summary "s" :rationale "routine call-centre operations coordination"
   :cites [campaign-id] :effect :propose :value {} :confidence 0.85})

(deftest campaign-unregistered-is-hard
  (testing "no campaign record at all -> HARD hold"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          verdict (gov/check {} nil (clean-proposal :log-call-record "unknown-campaign") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:campaign-unverified} (map :rule (:violations verdict)))))))

(deftest campaign-unverified-is-hard
  (testing "campaign registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"campaign-3" campaign-3})
          verdict (gov/check {} nil (clean-proposal :log-call-record "campaign-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:campaign-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-staffing-operation "campaign-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          verdict (gov/check {} nil (clean-proposal :finalize-do-not-call-override "campaign-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest do-not-call-override-finalization-content-is-hard
  (testing "a proposal whose rationale claims to finalize a do-not-call-list override is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          poisoned (assoc (clean-proposal :log-call-record "campaign-1")
                          :rationale "finalized the do-not-call override for this number"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest gdpr-ccpa-compliance-decision-content-is-hard
  (testing "a proposal claiming a GDPR/CCPA compliance decision is HARD-blocked"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          poisoned (assoc (clean-proposal :log-call-record "campaign-1")
                          :rationale "determined the campaign is GDPR compliant"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest consent-withdrawal-resolution-content-is-hard
  (testing "a proposal claiming a consent withdrawal was resolved is HARD-blocked"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          poisoned (assoc (clean-proposal :coordinate-equipment-supply "campaign-1")
                          :summary "consent withdrawal resolved for this contact")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest data-breach-determination-content-is-hard
  (testing "a proposal touching a finalized data-breach determination is HARD-blocked"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          poisoned (assoc (clean-proposal :schedule-staffing-operation "campaign-1")
                          :value {:decision "finalized the data breach determination"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest compliance-determination-content-is-hard
  (testing "a proposal touching a general privacy compliance determination is HARD-blocked"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          poisoned (assoc (clean-proposal :log-call-record "campaign-1")
                          :summary "issued a compliance determination for this campaign's consent status")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-privacy-concern-is-not-scope-excluded
  (testing "flagging observed do-not-call/consent-withdrawal/breach concerns as a PRIVACY CONCERN (not a compliance decision) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          concern (assoc (clean-proposal :flag-privacy-concern "campaign-1")
                         :value {:concern "contact reported an unconfirmed do-not-call request and disputed consent"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (do-not-call/consent disputes) is exactly what this op exists to surface"))))

(deftest flag-privacy-concern-always-escalates-even-when-clean
  (testing ":flag-privacy-concern is always high-stakes, regardless of confidence"
    (let [s (store/mem-store {"campaign-1" campaign-1})
          concern (assoc (clean-proposal :flag-privacy-concern "campaign-1") :confidence 0.99)
          verdict (gov/check {} nil concern s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

;; ----------------------------- self-trip regression -----------------------------
;;
;; Multiple sibling actors in this fleet independently discovered and fixed the
;; SAME bug class: scope-exclusion terms phrased as bare nouns accidentally
;; match inside the mock advisor's own default rationale/disclaimer text for a
;; legitimate, allowed proposal, causing the actor to self-block on its own
;; happy path. This test asserts the default mock advisor NEVER self-trips the
;; governor's scope-exclusion check for any allowed op, on a verified campaign.

(deftest no-default-proposal-self-trips-scope-exclusion
  (testing "the mock advisor's own default rationale/disclaimer text must never trip the governor's scope-exclusion check for any allowed op"
    (let [s (store/mem-store {"campaign-1" campaign-1})]
      (doseq [op [:log-call-record :schedule-staffing-operation
                  :coordinate-equipment-supply :flag-privacy-concern]]
        (let [p (advisor/infer nil {:op op :campaign-id "campaign-1"
                                    :patch {:concern "unconfirmed do-not-call request"
                                            :calls-handled 1 :shift "morning" :item "headsets"}})
              verdict (gov/check {} nil p s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "op " op " must not self-trip scope-exclusion on its own default rationale: "
                   (pr-str (:violations verdict)))))))))
