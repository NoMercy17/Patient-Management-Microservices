```mermaid
flowchart LR
    Frontend[Frontend client] -->|REST request JSON| Patient

    subgraph Docker[Docker network]
        Patient[Patient service] -->|gRPC request protobuf| Billing[Billing service]
    end
```