(ns softdev.advisor
  "SoftwareDevNECAdvisor — proposes a software-release operation
  (propose a release, publish a release) for a registered
  organization. Swappable mock/llm; the advisor ONLY proposes —
  `softdev.governor` computes the API-surface diff and the required
  semver bump independently. Modeled on cloud-itonami-isco-4311's
  advisor.

  A proposal: {:op :propose-release|:publish-release
               :effect :propose :module-id str
               :new-version [maj min patch] :new-surface #{str}
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake module-id new-version new-surface] :as request}]
  {:op op
   :effect :propose
   :module-id module-id
   :new-version new-version
   :new-surface new-surface
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a software release advisor. Given a request, propose an
   :op, the :module-id, :new-version and :new-surface, an honest
   :confidence and a :stake. Never call a breaking change a patch —
   the governor computes the surface diff and the required bump.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
