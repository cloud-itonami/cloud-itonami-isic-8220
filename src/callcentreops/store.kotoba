(ns callcentreops.store
  "SSoT for the ISIC-8220 call-centre operations COORDINATION actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every `cloud-itonami-isic-*` actor in this fleet uses.
  Two backends implement the protocol (mirrors `cerealops.store`,
  cloud-itonami-isic-0111):

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-
                        compatible EAV store. Pure `.cljc`, offline by
                        default and swappable to a real Datomic Local /
                        kotoba-server pod via `langchain.db`'s
                        `:db-api`. Campaign records are opaque,
                        caller-defined maps, so each is stored as a
                        single EDN-blob attribute (`langchain-store.core`'s
                        `enc`/`dec*`) rather than expanded into per-key
                        Datomic attributes -- the same convention every
                        sibling DatomicStore already uses for its own
                        opaque payloads. Both pass the same contract
                        (test/callcentreops/store_contract_test.clj).

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
  approved by whom is always a query over an immutable log."
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

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

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  `:campaign/payload` is an EDN-string blob (via `langchain-store.core`)
  so `langchain.db` doesn't try to expand an opaque, caller-defined
  campaign record into sub-entities. `:ledger/seq` and `:ops/seq` back
  the append-only audit ledger / committed-operations log as seq-keyed
  event streams (`langchain-store.core/read-stream` + `append-blob!`).
  The identity-schema builder, EDN-blob codec, and event-log helpers
  are the shared kotoba-lang/langchain-store machinery
  (ADR-2607141600) -- the seam ~190 actors hand-roll; this store keeps
  only its domain wiring."
  (ls/identity-schema [:campaign/id :ledger/seq :ops/seq]))

(defrecord DatomicStore [conn]
  Store
  (campaign [_store campaign-id]
    (when campaign-id
      (ls/dec* (d/q '[:find ?p .
                      :in $ ?cid
                      :where [?e :campaign/id ?cid] [?e :campaign/payload ?p]]
                    (d/db conn) campaign-id))))
  (all-campaigns [_store]
    (->> (d/q '[:find [?p ...]
                :where [?e :campaign/payload ?p]]
              (d/db conn))
         (mapv ls/dec*)
         (sort-by :campaign-id)))
  (ledger [_store] (ls/read-stream conn :ledger/seq :ledger/fact))
  (ops-log [_store] (ls/read-stream conn :ops/seq :ops/fact))
  (commit-record! [store record]
    (ls/append-blob! conn :ops/seq :ops/fact (count (ops-log store)) record)
    record)
  (append-ledger! [store fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger store)) fact)
    fact)
  (with-campaigns [store campaigns]
    ;; Upsert every campaign in `campaigns` (identity-schema on
    ;; :campaign/id means a re-put updates in place, mirroring
    ;; MemStore's plain `assoc`). Same documented limitation as every
    ;; sibling DatomicStore's `with-*`: does not retract campaigns
    ;; present in the OLD directory but absent from the new one -- this
    ;; actor only ever calls `with-campaigns` once, at seed time.
    (when (seq campaigns)
      (doseq [[campaign-id campaign-data] campaigns]
        (d/transact! conn [{:campaign/id campaign-id
                             :campaign/payload (ls/enc campaign-data)}])))
    store))

(defn datomic-seed-db
  "A DatomicStore (langchain.db backend) seeded with the same demo
  campaign directory as `seed-db`."
  []
  (let [s (->DatomicStore (d/create-conn schema))]
    (with-campaigns s (:campaigns (demo-data)))
    s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded with an explicit
  `campaigns` map -- the DatomicStore counterpart to `mem-store`."
  [campaigns]
  (let [s (->DatomicStore (d/create-conn schema))]
    (with-campaigns s (or campaigns {}))
    s))
