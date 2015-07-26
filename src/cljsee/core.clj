(ns cljsee.core
  (:require [cljsee.parse :refer [process-source-code]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.io.File))

;; Code mostly taken from cljx.core source but modified to find cljc files

(def ^:private warning-str ";;;;;;;;;;;; This file autogenerated from ")

(defn- cljc-source-file?
  [^File file]
  (and (.isFile file)
       (.endsWith (.getName file) ".cljc")))

(defn- find-cljc-sources-in-dir
  [^File dir]
  (sort-by #(.getAbsolutePath ^File %)
           (filter cljc-source-file? (file-seq (io/file dir)))))

(defn- relativize
  [f root]
  (-> root io/file .toURI (.relativize (-> f io/file .toURI))))

(defn- destination-path
  [source source-path output-dir]
  (.getAbsolutePath (io/file output-dir (str (relativize source source-path)))))

(defn generate
  ([options]
   (generate options (find-cljc-sources-in-dir (:source-path options))))
  ([{:keys [source-path output-path rules] :as options} files]
   (println "Rewriting" source-path "to" output-path
            (str "(" rules ")"))
   (doseq [f files
           :let [result (process-source-code (slurp f) #{rules})
                 destination (str/replace (destination-path f source-path output-path)
                                          #"\.[^\.]+$"
                                          (str "." (name rules)))]]
     (doto destination
       io/make-parents
       (spit (with-out-str
               (println result)
               (print warning-str)
               (println (.getPath f))))))))

(defn cljsee-compile
  ([builds & {:keys [files]}]
   (doseq [{:keys [source-paths output-path rules] :as build} builds
           p source-paths
           :let [abs-path (.getAbsolutePath (io/file p))]]
     (if files
       (when-let [files (->> files
                             (filter #(.startsWith (.getAbsolutePath (io/file %)) abs-path))
                             seq)]
         (generate (assoc build :rules rules :source-path p) files))
       (generate (assoc build :rules rules :source-path p))))))
