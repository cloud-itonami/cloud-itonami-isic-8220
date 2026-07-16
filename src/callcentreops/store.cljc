(ns callcentreops.store
  "SSoT for the ISIC-8220 call-centre operations COORDINATION actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the back-office operations of a call-centre
  service provider running inbound support and outbound telemarketing
  campaigns on behalf of client businesses: call-volume/handling-time/
  outcome data logging, agent-shift staffing scheduling, telephony-
  equipment procurement coordination, and privacy/consent-concern
  flagging (an unconfirmed do-not-call request, a consent-withdrawal
  claim, a possible data breach). It NEVER directly finalizes a
  data-privacy-compliance decision (resolving a do-not-call-list
  override, adjudicating a consent-withdrawal request, ruling on a
  GDPR/CCPA compliance determination, or finalizing a data-breach
  determination) -- see `callcentreops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable
  block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/
  demo (no deps). A `campaigns` directory keyed by `:campaign-id`
  STRING (never a keyword -- consistent keying from the start, avoiding
  the silent-miss bug that plagued an earlier shepherd attempt).

  A registered/verified client-campaign record must exist before ANY
  proposal for that campaign may ever commit or escalate --
  `callcentreops.governor`'s `campaign-unverified-violations`
  re-derives this from the campaign's own `:registered?`/`:verified?`
  fields, never from proposal self-report, the SAME 'ground truth, not
  self-report' discipline every sibling actor's own governor uses.

  The ledger stays append-only: which campaign a proposal targeted,
  which operation, on what basis, committed/held/escalated and
  approved by whom is always a query over an immutable log.")

(defprotocol Store
  (campaign [s campaign-id] "Registered client-campaign record, or nil.
    Campaign map: {:campaign-id .. :client .. :contract-type ..
    :registered? bool :verified? bool}. `:contract-type` is one of
    :inbound-support (inbound customer-support contact operations) or
    :outbound-telemarketing (outbound telemarketing/collections contact
    operations) -- purely descriptive, the governor treats both
    uniformly.")
  (all-campaigns [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (ops-log [s] "the append-only committed operations-record history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-campaigns [s campaigns] "replace/seed the campaign directory (map campaign-id->campaign)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained client-campaign directory covering both the
  happy path and the governor's own hard checks, so the actor + tests
  run offline."
  []
  {:campaigns
   {"campaign-1" {:campaign-id "campaign-1" :client "Acme Retail Support Line"
                   :contract-type :inbound-support :registered? true :verified? true}
    "campaign-2" {:campaign-id "campaign-2" :client "Northwind Outbound Renewals"
                   :contract-type :outbound-telemarketing :registered? true :verified? true}
    "campaign-3" {:campaign-id "campaign-3" :client "Riverside Collections (in intake, contract unconfirmed)"
                   :contract-type :outbound-telemarketing :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (campaign [_ campaign-id] (get-in @a [:campaigns campaign-id]))
  (all-campaigns [_] (sort-by :campaign-id (vals (:campaigns @a))))
  (ledger [_] (:ledger @a))
  (ops-log [_] (:ops-log @a))
  (commit-record! [_ record]
    (swap! a update :ops-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-campaigns [s campaigns] (when (seq campaigns) (swap! a assoc :campaigns campaigns)) s))

(defn seed-db
  "A MemStore seeded with the demo campaign directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :ops-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `campaigns` map (campaign-id
  string -> campaign map) -- the primary test/dev entry point.
  `campaigns` may be empty (an unregistered-everywhere store)."
  [campaigns]
  (->MemStore (atom {:campaigns (or campaigns {}) :ledger [] :ops-log []})))
