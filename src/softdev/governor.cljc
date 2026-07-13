(ns softdev.governor
  "SoftwareDevNECGovernor — the independent safety/traceability layer
  for the ISCO-08 2519 community software-development (NEC) actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-4311's bookkeeping.governor. Software
  twist: a breaking change is a NON-EMPTY SET DIFFERENCE between the
  registered API surface and the proposed one, and the version bump it
  requires is arithmetic — semver is a contract computed from the
  diff, not a mood chosen at release time.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. module basis      — a release must cite a REGISTERED module
                           belonging to this client.
    4. version advance   — the proposed version must be strictly
                           greater than the registered one
                           (lexicographic on [major minor patch];
                           re-releases and rollbacks are arithmetic
                           violations).
    5. semver discipline — removed symbols (registered surface minus
                           proposed surface) require a MAJOR bump;
                           added symbols with no removals require at
                           least a MINOR bump.
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :publish-release (external registry publication).
    7. low confidence (< `confidence-floor`)."
  (:require [clojure.set :as set]
            [softdev.store :as store]))

(def confidence-floor 0.6)

(defn- version< [a b]
  (neg? (compare (vec a) (vec b))))

(defn- hard-violations [{:keys [request proposal]} client-record m]
  (let [{:keys [op module-id new-version new-surface]} proposal
        release? (contains? #{:propose-release :publish-release} op)
        cur-version (:version m)
        cur-surface (set (:api-surface m))
        removed (when m (set/difference cur-surface (set new-surface)))
        added (when m (set/difference (set new-surface) cur-surface))
        major-bumped? (and m new-version
                           (> (first new-version) (first cur-version)))
        minor-or-more? (and m new-version
                            (or (> (first new-version) (first cur-version))
                                (and (= (first new-version) (first cur-version))
                                     (> (second new-version) (second cur-version)))))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and release? (nil? module-id))
      (conj {:rule :no-module :detail "release は module の引用が必須"})

      (and release? module-id (nil? m))
      (conj {:rule :unknown-module :detail (str "未登録 module: " module-id)})

      (and release? m (not= (:client-id m) (:client-id request)))
      (conj {:rule :module-wrong-client :detail "module が別 client のもの"})

      (and release? m new-version
           (not (version< cur-version new-version)))
      (conj {:rule :version-not-advanced
             :detail (str "version " new-version " は現行 " cur-version
                          " から前進していない（再リリース/ロールバックは算術違反）")})

      (and release? m new-version (seq removed) (not major-bumped?))
      (conj {:rule :breaking-without-major
             :detail (str "API surface から " (vec removed) " が消えるのに "
                          "major が上がっていない（破壊的変更は集合差で機械検出できる。"
                          "semver は差分から計算される契約であって気分ではない）")})

      (and release? m new-version (empty? removed) (seq added) (not minor-or-more?))
      (conj {:rule :addition-without-minor
             :detail (str "API surface に " (vec added) " が増えるのに "
                          "minor が上がっていない（追加は minor 以上）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `softdev.store/Store`. Pure — never mutates
  the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        m (some->> (:module-id proposal) (store/module store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record m)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :publish-release (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
