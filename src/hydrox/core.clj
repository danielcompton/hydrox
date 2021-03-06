(ns hydrox.core
  (:require [hydrox.meta.util :as util]
            [hydrox.meta :as meta]
            [hydrox.analyse :as analyser]
            [hydrox.common.data :as data]
            [hara.common.watch :as watch]
            [hara.event :as event]
            [hara.component :as component]
            [hara.io.watch]
            [hara.data.diff :as diff]
            [clojure.java.io :as io])
  (:refer-clojure :exclude [import]))

(def ^:dynamic *running* #{})

(defn read-project
  ([] (read-project (io/file "project.clj")))
  ([file]
   (let [path  (.getCanonicalPath file)
         root  (subs path 0 (- (count path) 12))
         pform (read-string (slurp file))
         [_ name version] (take 3 pform)
         proj  (->> (drop 3 pform)
                    (concat [:name name
                             :version version
                             :root root])
                    (apply hash-map))]
     (-> proj
         (update-in [:source-paths] (fnil identity ["src"]))
         (update-in [:test-paths] (fnil identity ["test"]))))))

(defn create-folio [{:keys [root] :as project}]
  (data/folio {:meta        {}
               :articles    {}
               :namespaces  {}
               :project     project
               :references  (data/references)
               :registry    (data/registry)
               :root        root}))

(defn mount-folio [state {:keys [project root] :as folio}]
  (let [{:keys [source-paths test-paths]} project]
    (watch/add (io/as-file root) :hydrox
               (fn [_ _ _ [type file]]
                 (println "File Changed:" file)
                 (case type
                   :create (swap! state analyser/add-file file)
                   :modify (swap! state analyser/add-file file)
                   :delete (swap! state analyser/remove-file file)))
               {:filter  [".clj"]
                :includes (->> (concat source-paths test-paths)
                               (map #(str root "/" %)))})
    folio))

(defn unmount-folio [{:keys [root] :as folio}]
  (if root
    (do (watch/remove (io/as-file root) :hydrox)
        true)
    false))

(defn init-folio [{:keys [project] :as folio}]
  (reduce (fn [folio file]
            (analyser/add-file folio file))
          folio
          (concat (util/all-files project :source-paths ".clj")
                  (util/all-files project :test-paths ".clj"))))

(defrecord Regulator [state project]

  component/IComponent
  (-start [obj]
    (let [folio (create-folio project)
          folio (if (:initialise folio)
                  (init-folio folio)
                  folio)]
      (mount-folio state folio)
      (reset! state folio)
      (event/signal [:log {:msg (str "Regulator for " (:name project) " started.")}])
      (alter-var-root #'*running* (fn [s] (conj s obj)))
      obj))

  (-stop  [obj]
    (unmount-folio @state)
    (reset! state nil)
    (event/signal [:log {:msg (str "Regulator for " (:name project) " stopped.")}])
    (alter-var-root #'*running* (fn [s] (disj s obj)))
    obj)

  (-stopped? [obj]
    (nil? @state)))

(defn regulator
  ([]
   (regulator (read-project)))
  ([project]
   (Regulator. (atom nil) project))
  ([state project]
   (Regulator. state project)))

(defn import-docstring
  ([reg] (import-docstring reg :all))
  ([reg ns] (import-docstring reg ns nil))
  ([{:keys [state project] :as reg} ns var]
   (let [{:keys [references]
          lu :namespace-lu} @state]
     (cond (= ns :all)
           (meta/import-project project references)

           :else
           (if-let [file (get lu ns)]
             (if var
               (meta/import-var file var references)
               (meta/import-file file references)))))))

(defn import [& args]
  (doseq [reg *running*]
    (apply import-docstring reg args)))

(defn purge-docstring
  ([reg] (purge-docstring reg :all))
  ([reg ns] (purge-docstring reg ns nil))
  ([{:keys [state project] :as reg} ns var]
   (let [{lu :namespace-lu} @state]
     (cond (= ns :all)
           (meta/purge-project project)

           :else
           (if-let [file (get lu ns)]
             (if var
               (meta/purge-var file var)
               (meta/purge-file file)))))))

(defn purge [& args]
  (doseq [reg *running*]
    (apply purge-docstring reg args)))

(comment
  (def reg (component/start (regulator (read-project (io/file "../hara/project.clj")))))

  hara.io.watch/*filewatchers*
  (component/stop reg)
  (component/stop reg)
  (unmount-folio @(:state reg))
  *running*
  (def reg (let [proj  (read-project)
                 folio (-> proj
                           (create-folio)
                           (init-folio))
                 state (atom folio)]
             (Regulator. state proj)))

  (def reg (let [proj  (read-project)
                 folio (-> proj
                           (create-folio)
                           (analyser/add-file (io/file "src/hydrox/analyser/test.clj"))
                           (analyser/add-file (io/file "test/hydrox/analyser/test_test.clj")))
                 state (atom folio)]
             (Regulator. state proj)))

  (.getCanonicalPath (io/file "src"))
  
  (:references @(:state reg))

  (import-docstring reg 'hydrox.doc.structure)
  (purge-docstring reg 'hydrox.analyse.test)

  @(:state reg)
  (:project reg)
  (.getParent(io/file "project.clj")))
