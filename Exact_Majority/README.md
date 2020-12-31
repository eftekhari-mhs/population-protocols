## A stable majority population protocol using logarithmic time and states

This is a repository for the exact majority protocol in [this](paperlink) paper. 
The Java simulator is available via [Agent](./Agent.java). The simulator goes through phases 0 to 9 of the protocol described in the paper. It takes snapshots of all agents' state in every n interactions ~roughly 1 unit of time. (n is the population size)

Open the [json_interpreter](./json_interpreter.ipynb) notebook to see the related plots of our simulations. The following is a sketch of the JSON Schema we use to store results from a simulation of our protocol. The outermost level maps the interaction number of each population snapshot we have taken (commonly taken every n interactions corresponding to a unit of parallel time).

The structure of one of these snapshots is a list of pairs (state, count). A state is given as a map from each of the fields used in the protocol to the value of the field.
```yaml
{"interactions":{
"0":[
  [
    {
      "phase": 0,
      "output": "",
      "role": "MCR",
      "hour": -1,
      "bias": -1,
      "counter": -1,
      "sample": 1,
      "exponent": 0,
      "minute": -1,
      "full": false
    },
    2561332
  ],
  [
    {
      "phase": 0,
      "output": "",
      "role": "MCR",
      "hour": -1,
      "bias": 1,
      "counter": -1,
      "sample": 1,
      "exponent": 0,
      "minute": -1,
      "full": false
    },
    2561334
  ]
],
"5122666":[
  [
    {
      "phase": 0,
      "output": "",
      "role": "Clock",
      "hour": -1,
      "bias": 0,
      "counter": 109,
      "sample": 1,
      "exponent": 0,
      "minute": -1,
      "full": false
    },
    953
  ],
  [
    {
      "phase": 0,
      "output": "",
      "role": "Main",
      "hour": -1,
      "bias": 2,
      "counter": -1,
      "sample": 1,
      "exponent": 0,
      "minute": -1,
      "full": false
    },
    245679
  ],
  [{...},86965],
  [{...},69133],
  [{...},470076],
  [{...},69719],
  [{...},69133],
  ... , 
  [{...},244372],
  [{...},491343],
  [{...},1],
  [{...},43]
],
"10245332":[ ... ],
"15367998":[ ... ],
...,
}
