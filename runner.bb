#! /usr/bin/env bb

;;;; Tiny, specialised task runner. 
;;; Good enough, but much better options exist.
;;; E.g.: Babashka's own task runner feature.

(require '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.tools.cli :as cli :refer [parse-opts]]
         '[clojure.string :as s :refer [join split]]
         '[clojure.java.io :refer [file]])

(def os
  (let [os (s/lower-case (java.lang.System/getProperty "os.name"))]
    (cond
      (s/starts-with? os "win") :windows
      (s/starts-with? os "mac") :macos
      :else :unix)))

(def opts (parse-opts *command-line-args*
                      [[nil "--edn EDN" "Explicit runner configuration" :default nil]
                       ["-u" "--unique" "Do not repeat individual actions" :default false]
                       ["-p" "--parallel" "Run all tasks in parallel" :default false]
                       ["-h" "--help"]]))

(def tasks (or (get-in opts [:options :edn])
               (let [f (file (if (= os :windows)
                               "runner.windows.edn"
                               "runner.edn"))]
                 (.createNewFile f)
                 (edn/read-string (slurp f)))
               {}))

(when (not (map? tasks))
  (println "ERROR:" "Runner tasks must be defined within a hash map!")
  (System/exit 1))

(defn get-tasks
  ([paths]
   (get-tasks paths []))
  ([[path & paths] ts]
   (if path
     (when-let [t (get-in tasks path)]
       (if (map? t)
         (let [ks (keys t)
               ps (mapv (partial conj path) ks)]
           (recur (into ps paths) ts))
         (recur paths (conj ts t))))
     ts)))

(defn decode-path
  [arg]
  (mapv keyword (split arg #":")))

(comment
  (with-redefs [tasks (edn/read-string (slurp "runner.example.edn"))]
    [(get-tasks [(decode-path "1-2:2-2")])
     (get-tasks [[:1-2 :2-2]])
     (get-tasks [[:1-2]]) ; entire section, depth-first
     (get-tasks [])
     (get-tasks [[]]) ;entire thing
     ]))

(defn get-actions
  [args]
  (for [arg args]
    (let [path (decode-path arg)
          ts (get-tasks [path])]
      (if (empty? ts)
        (throw (Exception. (str "No such action: " (join " " path))))
        ts))))

(def transformations
  (filter some? [(when (get-in opts [:options :unique]) distinct)]))

(defn transform-actions
  [actions]
  (loop [actions actions
         [t & ts] transformations]
    (if t
      (recur (t actions) ts)
      actions)))

(defn parallel?
  [action]
  (or (get-in opts [:options :parallel])
      (get (meta action) :parallel)))

(defn process-action
  [action]
  (let [cmd (if (string? action)
              action
              (s/join " " action))]
    (if (parallel? action)
      (p/process cmd {:inherit true :shutdown p/destroy-tree})
      (p/shell cmd))))

(defn help
  []
  (println "Usage:\n" "bb " *file* "<Options> <Tasks>")
  (println "Options:\n" (:summary opts))
  (println "Tasks:\n"
           "Assuming the contents of your runner.edn are as follows:\n"
           {:first-task "first-task"
            :task-category {:second-task "second-task"
                            :third-task "third-task"}}
           "\n"
           "To address the third task use `first-task:third-task`.\n"
           "You can also call the entire category, which will be processed top-down."))

(try
  (if (get-in opts [:options :help])
    (help)
    (let [actions (->> (:arguments opts)
                      get-actions
                      (reduce into [])
                      transform-actions)]
      (if (empty? actions)
        (help)
        (let [procs (map process-action actions)]
          (dorun procs)
          (doseq [p procs] @p)))))
  (catch Exception e
    (println (.getMessage e))))
