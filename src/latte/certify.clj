(ns latte.certify
  "Certification of LaTTe namespaces.
  The functions in this namespace allow to take a snapshot of
  the current running state of a namespace, and issue a certificate
  of all the theorems demonstrated (using a cryptographic signature scheme).
  This is a form of compilation for demonstrated theorems."
  
  (:require [digest]
            [latte.utils :as u]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; some dummy utilities, perhaps not needed (otherwise they'd go to the `utils`

(defn fetch-namespaces-with-prefix
  [prefix]
  (reduce (fn [namespaces ns-obj]
            (if (string/starts-with? (str (ns-name ns-obj)) prefix)
              (conj namespaces ns-obj)
              namespaces)) [] (all-ns)))


;; ========================
;; The certificate database
;; ========================

(defonce +global-proof-certificate+ {})



;; ===========================
;; The certification functions
;; ===========================

(def ^:dynamic *verbose-certification* true)

(defn mk-timestamp-file! [library-name]
  (when *verbose-certification*
    (println "..Generating certification timestamp")
    (println "  ==> library:" library-name))
  (let [timestamp (java.util.Date.)
        _ (when *verbose-certification* (println "  ==> date:" (str timestamp)))
        timestamp-filename "resources/cert/timestamp.edn"
        timestamp-file (io/file timestamp-filename)]
    ;; erase old timestamp file, if any
    (when (.exists timestamp-file)
      (when *verbose-certification* (println "  ==> deleting old timestamp file"))
      (io/delete-file timestamp-file))
    (when *verbose-certification*
      (println "  ==> write file:" (str "'" timestamp-filename "'")))
    ;; spit timestamp
    (spit timestamp-file {:library library-name
                          :timestamp timestamp })
    (when *verbose-certification*
      (println "  ==> done"))))

;; (mk-timestamp-file! "latte")

(defn clear-certification! []
  (when *verbose-certification*
    (println "..Clear certificate (cleaning up 'resource/cert' directory)"))
  (let [cert-dir (io/file "resources/cert")]
    (if (.exists cert-dir)
      (do (doseq [cert-file (.listFiles cert-dir)]
            (when *verbose-certification*
              (println "  ==> Deleting old certification file:" (.getName cert-file)))
            (.delete cert-file))
          ;; delete cert directory
          (when *verbose-certification*
            (println "  ==> Deleting old certification directory: " (.getName cert-dir)))
          (.delete cert-dir))
      ;; no cert directory
      (when *verbose-certification*
        (println "  ==> no certification directory")))))

;; (clear-certification!)

(defn demonstrated-theorems
  "Fetch a map of theorems with an actual proof in the namespace
  named `namesp' (a symbol). If not indicated the current namespace is used."
  ([namesp]
   (let [thms (:theorems (u/fetch-ns-elements (the-ns namesp)))]
     thms))
  ([] (demonstrated-theorems *ns*)))

;; (demonstrated-theorems 'latte.prop)

(defn theorem-signature [params type proof]
  "Sign the specified theorem contents."
  (digest/sha-256 (str params "/" type "/" proof)))

(defn certified-theorems
  "Build a map of theorem certifications from a map `thms' of theorems.
  Each theorem contents (definition ) is signed with "
  [thms]
  (into {} (reduce (fn [cthms [thm-name thm-content]]
                     (if (:proof thm-content)
                       (conj cthms [thm-name (theorem-signature (:params thm-content) (:type thm-content) (:proof thm-content))])
                       cthms)) [] thms)))

;; (certified-theorems (demonstrated-theorems 'latte.prop))

(defn certify-namespace! [namesp]
  (when *verbose-certification*
    (println "..Certify namespace:" namesp)
    (println "  ==> certification started ..."))
  (if-let [thms (demonstrated-theorems namesp)]
    (let [thm-certs (certified-theorems thms)]
      (when *verbose-certification*
        (println "  ==> ... done all" (count thm-certs) "theorem(s) certified"))
      (let [cert-filename (str "resources/cert/" namesp ".cert")]
        (when *verbose-certification*
          (println "  ==> writing certification file:" cert-filename))
        (spit cert-filename thm-certs)
        (when *verbose-certification*
          (println "  ==> done"))))
    ;; no theorem
    (when *verbose-certification*
      (println "  ==> no theorem to certify (do nothing)"))))

;; (certify-namespace! 'latte.prop)

(defn certify-library! [library-name namespaces]
  (when *verbose-certification*
    (println "=== Certifying library:" library-name)
    (println "  ==> namespaces:" namespaces "==="))
  ;; 1) clear the certificate
  (clear-certification!)
  ;; 2) create the certificate directory
  (let [cert-dir (io/file "resources/cert")]
    (.mkdir cert-dir)
    (when *verbose-certification*
      (println " ==> certification directory created"))
    ;; 3) create timestamp file
    (mk-timestamp-file! library-name)
    ;; 4) certify namespaces
    (doseq [namesp namespaces]
      (certify-namespace! namesp))
    (println "=== Certificate issued ==")))
