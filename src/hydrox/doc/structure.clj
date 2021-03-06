(ns hydrox.doc.structure)

(def containers
  #{:article :chapter :appendix :section :subsection :subsubsection :generic})

(def hierarchy
  (-> (make-hierarchy)
      (derive :article :generic)
      (derive :article :chapter)
      (derive :article :appendix)
      (derive :container :section)
      (derive :appendix :section)
      (derive :chapter :section)
      (derive :section :subsection)
      (derive :subsection :subsubsection)))

(defn inclusive
  "determines which sections are contained by the other
   (inclusive :article :section) => true
 
   (inclusive :chapter :subsection) => true
 
   (inclusive :chapter :chapter) => false
 
   (inclusive :section :chapter) => false"
  {:added "0.1"}
  [x y]
  (if (get containers y)
    (if (not (isa? hierarchy y x))
      true
      false)
    true))

(defn seperate
  "groups elements in an array 
   (seperate #(= 1 %) [1 2 2 1 3 4 5])
   => [[1 2 2] [1 3 4 5]]"
  {:added "0.1"}
  ([f v]
   (seperate f (rest v) [] (if (first v)
                             [(first v)]
                             [])))
  ([f [ele & more] all current]
   (cond (nil? ele)
         (conj all current)

         (f ele)
         (recur f more (conj all current) [ele])

         :else
         (recur f more all (conj current ele)))))

(defn containify
  
  {:added "0.1"}
  ([v]
   (->> (containify v [#{:appendix :chapter :generic} :section :subsection :subsubsection])
        (cons {:type :article})
        vec))
  ([v [tag & more]]
   (let [eq? (fn [tag ele]
               (if (set? tag)
                 (get tag (:type ele))
                 (= tag (:type ele))))
         subv (seperate (fn [ele] (eq? tag ele)) v)]
     (cond (empty? more)
           subv

           :else
           (mapv (fn [[head & body]]
                   (cond
                     (empty? body) [head]

                     :else
                     (vec (cons head (containify body more)))))
                subv)))))

(declare mapify)

(defn mapify-unit [x]
  (cond (map? x) x
        :else (mapify x)))

(defn mapify [[head & more]]
  (cond (get containers (:type head))
        (assoc head :elements (vec (flatten (map mapify-unit more))))

        :else
        (mapv mapify-unit (cons head more))))


(defn structure
  
  {:added "0.1"}
  [v]
  (-> v
      (containify)
      (mapify)))
