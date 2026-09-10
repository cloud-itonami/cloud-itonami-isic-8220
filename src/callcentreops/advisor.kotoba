(ns callcentreops.advisor
  "CallCentreAdvisor -- the *contained intelligence node* for the
  ISIC-8220 call-centre operations-coordination actor.

  It drafts exactly five kinds of back-office proposal from a closed
  allowlist: call-record logging, staffing-operation scheduling,
  telephony-equipment supply coordination, privacy-concern flagging,
  and -- since ADR-2607264000 -- referring a contact to marketplace
  dispute intake. CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream
  by `callcentreops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a proposal that directly finalizes a
  data-privacy-compliance decision (resolving a do-not-call-list
  override, adjudicating a consent-withdrawal request, a GDPR/CCPA
  compliance ruling, or finalizing a data-breach determination) --
  that is permanently out of scope for this actor, not merely
  un-implemented. `callcentreops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal
  for exactly this failure mode (a compromised or confused advisor
  drifting into scope it must never touch) and HARD-holds it,
  regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :campaign-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}"
  (:require [marketplace.support :as support]))

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-call-log
  "Draft a call-volume/handling-time/outcome data logging entry. Pure
  logging of a campaign's factual call-handling metrics -- never a
  privacy-compliance determination."
  [_db {:keys [campaign-id patch]}]
  {:op         :log-call-record
   :campaign-id campaign-id
   :summary    (str campaign-id " の通話記録(件数/対応時間/結果)を更新: " (pr-str (keys patch)))
   :rationale  "コールセンター運用の通話量・対応時間・結果データの記録のみ。データプライバシー・コンプライアンス判断は含まない。"
   :cites      [campaign-id]
   :effect     :propose
   :value      (merge {:campaign-id campaign-id} patch)
   :confidence 0.92})

(defn- propose-staffing-operation
  "Draft an agent-shift staffing-schedule proposal (a roster/coverage
  entry, never a direct dispatch or contract action)."
  [_db {:keys [campaign-id patch]}]
  {:op         :schedule-staffing-operation
   :campaign-id campaign-id
   :summary    (str campaign-id " の要員シフト編成を提案: " (pr-str (keys patch)))
   :rationale  "エージェントのシフト編成調整のみ。最終的な人員配置決定は人間の運用責任者が行う。"
   :cites      [campaign-id]
   :effect     :propose
   :value      (merge {:campaign-id campaign-id} patch)
   :confidence 0.87})

(defn- propose-equipment-supply
  "Draft a telephony-equipment procurement-coordination proposal
  (headsets/lines/CTI licensing logistics, never a direct purchase
  order dispatch)."
  [_db {:keys [campaign-id patch]}]
  {:op         :coordinate-equipment-supply
   :campaign-id campaign-id
   :summary    (str campaign-id " に関連する電話設備の調達調整を提案: " (pr-str (keys patch)))
   :rationale  "通話設備(ヘッドセット/回線/CTIライセンス)の調達ロジスティクス調整のみ。個人データの取扱い可否判断は含まない。"
   :cites      [campaign-id]
   :effect     :propose
   :value      (merge {:campaign-id campaign-id} patch)
   :confidence 0.85})

(defn- propose-privacy-concern
  "Surface a data-consent/do-not-call/data-breach concern (an
  unconfirmed do-not-call request, a disputed consent-withdrawal
  claim, a possible data breach) for HUMAN triage. This op ALWAYS
  escalates in `callcentreops.governor` -- never auto-committed at any
  phase -- regardless of how confident the advisor is that the concern
  is real. It NEVER resolves the underlying privacy-compliance
  question itself."
  [_db {:keys [campaign-id patch]}]
  {:op         :flag-privacy-concern
   :campaign-id campaign-id
   :summary    (str campaign-id " のプライバシー/同意懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "架電先の同意撤回・発信禁止登録・データ侵害に関する観察事実の報告のみ。コンプライアンス判断は行わず、常に人間の確認が必要。"
   :cites      [campaign-id]
   :effect     :propose
   :value      (merge {:campaign-id campaign-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn- propose-dispute-referral
  "Refer a support contact to marketplace dispute intake.

  ALWAYS escalates. The caller's own account and the agent's note are
  kept ATTRIBUTED and SEPARATE by `marketplace.support/referral` --
  merging them is how an agent's inference quietly becomes part of the
  buyer's testimony. Nothing here decides anything; the governor rejects
  any referral carrying an outcome."
  [_db {:keys [campaign-id patch]}]
  (let [r (support/referral
           {:id (:referral-id patch)
            :ticket-id (:ticket-id patch)
            :order (:order patch)
            :buyer (:buyer patch)
            :seller (:seller patch)
            :reason (:reason patch)
            :claimed-by-caller (:claimed-by-caller patch)
            :agent (:agent patch)
            :agent-note (:agent-note patch)
            :referred-at (:referred-at patch)})]
    {:op :refer-to-dispute
     :campaign-id campaign-id
     :summary (str (:ticket-id patch) " の応対を注文 " (:order patch)
                   " の紛争受付へ照会: " (pr-str (:reason patch)))
     :rationale "応対内容を紛争受付へ引き継ぐ照会のみ。責任の所在や返金の可否は判断しない。"
     :cites (vec (keep identity [(:ticket-id patch) (:order patch)]))
     :effect :propose
     :value {:referral r}
     :confidence (or (:confidence patch) 0.85)}))

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :refer-to-dispute (propose-dispute-referral _db request)
                   :log-call-record (propose-call-log _db request)
                   :schedule-staffing-operation (propose-staffing-operation _db request)
                   :coordinate-equipment-supply (propose-equipment-supply _db request)
                   :flag-privacy-concern (propose-privacy-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- 実際にはこのキャンペーンの発信禁止リスト解除確定を行い、同意撤回解決確定とした")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :campaign-id (:campaign-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
