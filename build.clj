(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]))

(def lib 'com.github.hden/ulid)
(def version (str/trim (slurp "VERSION")))
(def target-dir "target")
(def class-dir (str target-dir "/classes"))
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def jar-file (format "%s/%s-%s.jar" target-dir (name lib) version))
(def pom-file
  (format "%s/META-INF/maven/%s/%s/pom.xml"
          class-dir (namespace lib) (name lib)))

(defn clean [_]
  (b/delete {:path target-dir}))

(defn jar [_]
  (clean nil)
  (b/write-pom
    {:class-dir class-dir
     :lib lib
     :version version
     :basis @basis
     :src-pom :none
     :src-dirs ["src"]
     :scm {:url "https://github.com/hden/ulid"
           :connection "scm:git:https://github.com/hden/ulid.git"
           :developerConnection "scm:git:ssh://git@github.com/hden/ulid.git"}
     :pom-data
     [[:description "A small monotonic ULID generator for Clojure"]
      [:url "https://github.com/hden/ulid"]
      [:licenses
       [:license
        [:name "MIT License"]
        [:url "https://opensource.org/license/mit/"]]]]})
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/jar {:class-dir class-dir :jar-file jar-file})
  {:jar-file jar-file :pom-file pom-file})

(defn install [_]
  (let [{:keys [jar-file]} (jar nil)]
    (b/install {:basis @basis
                :lib lib
                :version version
                :jar-file jar-file
                :class-dir class-dir})))

(defn deploy [_]
  (let [{:keys [jar-file pom-file]} (jar nil)]
    (deps-deploy/deploy
      {:installer :remote
       :artifact jar-file
       :pom-file pom-file
       :sign-releases? false})))
