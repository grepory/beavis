(ns leiningen.docker
    (:require [leiningen.jar :refer [get-jar-filename]]
      [leiningen.uberjar :refer [uberjar]]
      [clojure.java.shell :refer [sh]]
      [clojure.string :refer [trim blank?]]))

(defn docker
      "Build docker image"
      [project]
      (let [jar-path (get-jar-filename project :standalone)]
           (sh "cp" jar-path "docker/lib/beavis.jar")
           (let [docker (sh "docker" "build" "-t" "quay.io/opsee/beavis" "docker")
                 git-ref (sh "git" "rev-parse" "--verify" "HEAD")]
                (if-not (= 0 (:exit git-ref))
                        (println (:err git-ref))
                        (do
                          (println (:out docker))
                          (if-not (blank? (:err docker))
                                  (println "Build error: " (:err docker))
                                  (sh "docker" "build" "-t" (str "quay.io/opsee/beavis:" (trim (:out git-ref))) "docker")))))))
