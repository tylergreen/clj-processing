(ns processing.core.applet
  (:use [processing.core :except (size)])
  (:import (javax.swing JFrame)
           (java.awt.event WindowListener)))

(defn- fix-mname
  "Changes :method-name to :methodName."
  [[mname fun]]
  (let [mname (name mname)
        mr (re-matcher #"\-[a-zA-z]" mname)
        replace-fn (comp #(.replaceFirst mr %) toupper #(.substring % 1))
        fixed-name (if-let [matched (re-find mr)]
                     (replace-fn matched)
                     mname)]
    [(keyword fixed-name) fun]))


(defn applet-stop
  "Stop an applet"
  [applet]
  (.stop applet))

(defn applet-start
  "Start an applet"
  [applet]
  (.start applet))

(defn applet-exit
  "Exit the applet (may kill JVM process)"
  [applet]
  (.exit applet))

(defn applet-close
  "Stop the applet and close the window."
  [applet]
  (let [closing-fn (fn []
                     (let [frame @(:frame (meta applet))]
                       (.stop applet)
                       ;;.destroy appears to kill the process too
                       (doto frame
                         (.hide)
                         (.dispose))))]
    (javax.swing.SwingUtilities/invokeAndWait closing-fn))  )

(defn- applet-run
  "Launches the applet."
  ([applet] (applet-run applet nil))
  ([applet mode]
     (.init applet)
     (let [m (.meta applet)
           [width height & _] (or (:size m) [200 200])
           close-op (if (= :exit-on-close mode)
                      JFrame/EXIT_ON_CLOSE
                      JFrame/DISPOSE_ON_CLOSE)]
       (reset! (:frame m)
               (doto (JFrame. (or (:title m) (:name m)))
                 (.addWindowListener  (reify WindowListener
                                        (windowActivated [this e])
                                        (windowClosing [this e]
                                          (future (applet-close applet)))
                                        (windowDeactivated [this e])
                                        (windowDeiconified [this e])
                                        (windowIconified [this e])
                                        (windowOpened [this e])
                                        (windowClosed [this e])))
                 (.setDefaultCloseOperation close-op)
                 (.setSize width height)
                 (.add applet)
                 (.pack)
                 (.show))))))

(defn- applet-set-size
  ([width height] (.size *applet* (int width) (int height)))
  ([width height ^String renderer] (.size *applet* (int width) (int height) renderer)))

(defn applet
  [& opts]
  (let [options           (merge {:size [500 300]} (apply hash-map opts))
        fns               (dissoc options :name :title :size :key-pressed
                                  :key-released :mouse-pressed :mouse-released
                                  :mouse-moved :mouse-dragged :setup)
        fns               (into {} (map (fn [[k v]] [k (if (symbol? v) `(var ~v) v)]) fns))
        fns               (merge {:draw (fn [] nil)} fns)
        key-pressed-fn    (or (:key-pressed options) (fn [] nil))
        key-released-fn   (or (:key-released options) (fn [] nil))
        mouse-pressed-fn  (or (:mouse-pressed options) (fn [] nil))
        mouse-released-fn (or (:mouse-released options) (fn [] nil))
        mouse-moved-fn    (or (:mouse-moved options) (fn [] nil))
        mouse-dragged-fn  (or (:mouse-dragged options) (fn [] nil))
        setup-fn          (fn []
                            (apply applet-set-size (:size options))
                            (when (:setup options)
                              ((:setup options))))
        methods           (into {} (map fix-mname fns))
        frame             (atom nil)
        state             (atom nil)
        prx               (proxy [processing.core.PApplet
                                  clojure.lang.IMeta] []
                            (meta [] (assoc options :frame frame))
                            (keyPressed
                              ([] (binding [*applet* this
                                            *state* state]
                                    (key-pressed-fn)))
                              ([e]
                                 (proxy-super keyPressed e)))
                            (keyReleased
                              ([] (binding [*applet* this
                                            *state* state]
                                    (key-released-fn)))
                              ([e]
                                 (proxy-super keyReleased e)))
                            (mousePressed
                              ([] (binding [*applet* this
                                            *state* state]
                                    (mouse-pressed-fn)))
                              ([e]
                                 (proxy-super mousePressed e)))

                            (mouseReleased
                              ([] (binding [*applet* this
                                            *state* state]
                                    (mouse-released-fn)))
                              ([e]
                                 (proxy-super mouseReleased e)))

                            (mouseMoved
                              ([] (binding [*applet* this
                                            *state* state]
                                    (mouse-moved-fn)))
                              ([e]
                                 (proxy-super mouseMoved e)))

                            (mouseDragged
                              ([] (binding [*applet* this
                                            *state* state]
                                    (mouse-dragged-fn)))
                              ([e]
                                 (proxy-super mouseDragged e)))

                            (setup
                              ([] (binding [*applet* this
                                            *state* state]
                                    (setup-fn)))))

        bound-meths       (reduce (fn [methods [method-name f]]
                                    (assoc methods (name method-name)
                                           (fn [this & args]
                                             (binding [*applet* this
                                                       *state* state]
                                               (apply f args)))))
                                  {}
                                  methods)]
    (update-proxy prx bound-meths)
    (applet-run prx)
    prx))

(defmacro defapplet
  "Define an applet. Takes an app-name and a map of options."
  [app-name & opts]
  `(def ~app-name (applet ~@opts)))

(comment ;; Usage:
  (defapplet growing-triangle
    :draw (fn [] (line 10 10 (frame-count) 100)))

  (applet-stop growing-triangle)
  (applet-close growing-triangle))
