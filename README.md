donut.endpoint provides:

- a useful default middleware stack
- route group component for applying shared options to groups, working with
  donut.system
- default reitit route middleware for API endpoints; malli request coercion
  failures answer 422 with donut's error segment
  `[[:errors {:form [msg] :attrs {[:k] ["msg"]}}]]`
