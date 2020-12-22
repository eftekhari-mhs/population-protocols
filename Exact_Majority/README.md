## A stable majority population protocol using logarithmic time and states

This is a repository for the exact majority protocol in [this](paperlink) paper. 
The Java simulator is available via [Agent](./Agent.java). The simulator goes through phases 0 to 8 of the protocol described in the paper. It takes snapshots of all agents' state in every n interactions ~roughly 1 unit of time. (n is the population size)

The following is a sketch of the JSON Schema we use to store results from a simulation of our protocol. The outermost level maps the interaction number of each population snapshot we have taken (commonly taken every n interactions corresponding to a unit of parallel time).

The structure of one of these snapshots then shows an explicit way to represent the entire state set. At the outermost level we split based on the value of ``phase``, then within each possible phase we outline the structure of the possible states. The only information the agents store not explicitly noted in this schema is their ``input``. Also, the value of ``output``, which is only defined in the phases 1, 3, 7, 8 where stabilization is possible, is implicit from which state the agent is in.

```yaml
{
"configuration": {
  "0": {
    "field_name": "phase",
    "count": n,
    "children": {
      "0": {
        "field_name": "role",
        "count": int,
        "children": {
          "Reserve": {
            "field_name": "bias",
            "count": int,
            "children": {
              "A": {
                "field_name": "level",
                "count": int,
                "children": {
                  "-1": {
                    "field_name": "counter",
                    "count": int,
                    "children": 
                        {"0": {"count": int} },
                        {"1": {"count": int}},
                        ...,
                        {"int(c*ln(n))": {"count": int}}
                    }
                  }
                }
              }
            }
          },
          "Unassigned": {
            "field_name": "bias",
            "count": int,
            "children": {
              "T": {
                "field_name": "level",
                "count": int,
                "children": {
                  "11": {
                    "field_name": "counter",
                    "count": int,
                    "children": 
                        {"0": {"count": int} },
                        {"1": {"count": int}},
                        ...,
                        {"int(c*ln(n))": {"count": int}}                    
                    }
                  }
                }
              }
            }
          },
          "Clock": {
            "field_name": "bias",
            "count": int,
            "children": {
              "T": {
                "field_name": "level",
                "count": int,
                "children": {
                  "11": {
                    "field_name": "counter",
                    "count": int,
                    "children": 
                        {"0": {"count": int} },
                        {"1": {"count": int}},
                        ...,
                        {"int(c*ln(n))": {"count": int}}                                        
                    }
                  }
                }
              }
            }
          },
          "Main": {
            "field_name": "bias",
            "count": int,
            "children": {
              "A": {
                "field_name": "level",
                "count": int,
                "children": {
                  "11": {
                    "field_name": "counter",
                    "count": int,
                    "children": 
                        {"0": {"count": int} },
                        {"1": {"count": int}},
                        ...,
                        {"int(c*ln(n))": {"count": int}} 
                      }
                    }
                  }
                }
              },
              "B": {
              ...
              }
              "T": {
              ...
              }
            }
          }
        }
      }
    }
  }, ....
        "8":{
             "field_name": "role",
             "count": n,
             "children": {
             ...
             }            
        }
    },
  "int(2*n)": {...},
  ...,
  "int(t_max * n)": {...}
  }
}
