# Meetup - Event-Driven: Microserviços com Clojure

## Resumo

Microserviços são implementados usando uma pilha de tecnologia, que servem como um único domínio provendo serviços ao redor. O `pedestal` é um framework clojure usado para criar aplicações baseadas em microserviços

## Quem sou eu?

Nome: Enilton Angelim
Github: https://github.com/eniltonangelim

## Uma visão geral - Microserviços

Uma arquitetura `microservice` é um padrão de arquitetura de software altamente automatizado, e suporta interoperabilidade através de comunicação baseada em mensagens.

<img> big-ball

## Objetivos da arquitetura

O que faz a arquitetura microservice ser especial é a fácilidade de substituir um componente dentro do serviço.

<img> domain model

## Por que clojure?

Basicamente, porque:

+ É LISP
  + Code-as-data
+ Programação funcional
+ Plataforma JVM
+ Concorrência (Threads)
  + A imutabilidade faz com que grande parte do problema desapareça

## Uma visão geral - Clojure

Clojure é uma linguagem funcional que foca em imutabilidade, por causa disso não temos o conceito de variável como conhecemos em outras linguagens.

Vars
---

```clojure
(def x 22)
```

```clojure
(def y [1 2 3])
```

Functions
---

```clojure
(fn [x]
   "Retorna o quadrado de X"
  (* x x))
```

```clojure
(def quadrado
  (fn [x]
    (* x x)))
```

```clojure
(defn quadrado
  [x]
  (* x x))
```

Lists
---

```clojure
'(1 2 3 4)
```

Vector
---

```clojure
[1 2 3 4]
```

HashSet
---

```clojure
#{1 2 3 4 5 5 3}
```

Atom

```clojure
(def pessoa
  (atom
    {:nome "Enilton Angelim"
     :idade 28}))
```

```clojure
(swap! pessoa update :idade inc)
```

### Framework: Pedestal

Pedestal é um framework para criar API's em clojure.

Conceitos principais:

+ Context Map
+ Interceptors

<img> intercptors stack


### Biblioteca: SQLkorma

Usado para mapear uma entidades com uma tabela do banco

Entidades:

```clojure
(ns ws-meetup.model.meetup
  (:use korma.core)
  (:require 
    [buddy.hashers :as hashers]
    [clj-time.coerce :as time-coerce]
    [clj-time.jdbc]))

(declare users meetup)

(defentity users
  (pk :id)
  (entity-fields
    :id
    :username
    :password)
  (has-many meetup)
  (prepare 
    (fn [{:as user}]
      (update-in user 
        [:password] #(hashers/derive % {:alg :bcrypt+blake2b-512})))))

(defentity meetup
  (pk :id)
  (has-one users)
  (prepare
    (fn [{:as otime}]
      (if otime
        (-> otime
          (assoc :initial (time-coerce/to-sql-time (:initial otime)))
          (assoc :finale (time-coerce/to-sql-time (:finale otime))))
        otime)))
  (transform
    (fn [{:as otime}]
      (if (:id otime)
        (-> otime
          (assoc :initial (.toString (:initial otime)))
          (assoc :finale (.toString (:finale otime))))
        otime))))
```

Query:

```clojure
(ns ws-meetup.storage.users
  (:use
    korma.core
    ws-meetup.model.meetup
    ws-meetup.storage.db))

(defn add
  [credentials]
  (insert users
    (values credentials)))

(defn find
  [credentials]
  (select users
    (where {:username (:username credentials)})))
```

### Modulo: Buddy

```clojure
(ns ws-meetup.authentication.auth
    (:require 
      [ws-meetup.storage.users :as users]
      [ws-meetup.config :as config]
      [buddy.hashers :as hashers]
      [clojure.java.io :as io]
      [buddy.sign.jwt :as jwt]
      [buddy.core.keys :as keys]
      [clj-time.core :as t]))
  
(defn- check-password
  [credentials user-store]
  (if (hashers/check 
        (:password credentials)
        (:password user-store))
    [true {:user (dissoc user-store :password)}]
    [false {:message "Invalid username or password"}]))

(defn- authentication 
  [credentials]
  (let [user-store (try (first (users/find credentials))
                        (catch Exception _))]
    (if (:id user-store)
        (check-password credentials user-store)
        [false {:message "Invalid username or password"}])))

(defn- private-key 
  []
  (keys/private-key 
    (io/resource (config/authentication-certificated :privkey))
    (config/authentication-certificated :passphrase)))

(defn- expiration-time
  []
  (t/plus (t/now) (t/hours 1)))

(defn- sign-token [claims]
  (jwt/sign (assoc claims :exp (expiration-time))
    (private-key)
    {:alg :ps256}))

(defn unsign-token [token]
  (jwt/unsign token
    (keys/str->public-key (slurp 
                            (io/resource (config/authentication-certificated :pubkey))))
    {:alg :ps256}))

(defn create-authentication-token 
  [credentials]
  (let [[is-trusty? user-data] (authentication credentials)]
    (if is-trusty?
      [true {:token (sign-token user-data) :id (get-in user-data [:user :id])}]
      [false user-data])))
```

## Recursos

+ Buddy - Security library for clojure: https://github.com/funcool/buddy
+ Korma - SQL for Clojure: https://github.com/korma/Korma
+ Pedestal - Server-side Libraries: https://github.com/pedestal/pedestal
+ Learn X in Y minutes, Where X = Clojure: https://learnxinyminutes.com/docs/clojure/
+ Clojure - Meetup Fortaleza Chat: https://github.com/eniltonangelim/clojure-meetup
+ Clojure - Meetup Fortaleza Auth: https://github.com/eniltonangelim/clojure-meetup-auth