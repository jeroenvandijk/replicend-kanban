# Replicend Kanban

A proof of concept to replicate [Replicant-Kanban](https://github.com/cjohansen/replicant-kanban) in a [Datastar](https://data-star.dev/) kind of way (the backend streams the html to client for realtime updates).

Start with babashka
```
bb -x replicant-kanban/server
```

### Libraries used:

- [Reagami](https://github.com/borkdude/reagami): for morphing changes into the dom
- [Nexus](https://github.com/cjohansen/nexus): Dispatch system, both in front and backend
- [Scittle](https://github.com/babashka/scittle): to run frontend effects and actions and acts as a bridge to the backend (e.g. EDN serialization).

- [Replicant](https://github.com/cjohansen/replicant): because we try to render code from Replicant-Kanban which uses aliases, but otherwise not needed for the backend. Of course, the whole experiment is inspired by Replicant :-).
- [Httpkit](https://github.com/http-kit/http-kit): because I wanted to make it work in Babashka. In other experiments with this setup on the JVM I preferred Aleph + Manifold for SSE setups.
- [Babashka](https://github.com/babashka/babashka) as 'quick way' to get everything working (and tested).

To understand how it all works, first get familiar with the [replicant-Kanban](https://github.com/cjohansen/replicant-kanban) repo. Most of the code is coming from this repo and is loaded as a dependency. In `frontend/kanban/actions` you can see what the frontend can do, the rest is delegated to the backend (`kanban.actions` from `replicant-kanban`)


### Notes

- The drop effect of a card has some latency compared to `replicant-kanban`. You might notice as small flickering here (card moving shortly back to the original position until the server sends an updated view).