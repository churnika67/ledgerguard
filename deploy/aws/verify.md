# Verifying the AWS deploy

Run these from your Mac, against the EC2 instance's public IP (from `ids.env` or the EC2
console). Replace `<IP>` throughout.

## 1. Health check

```bash
curl http://<IP>:8080/actuator/health
# {"status":"UP", ...}
```

If this hangs or refuses, see the networking experiments below before assuming the app itself
is broken.

## 2. Trigger the statement job and confirm a real CSV lands in S3

```bash
BASE=http://<IP>:8080

# Get an admin token (same demo users as everywhere else in this project)
TOKEN=$(curl -s -X POST "$BASE/auth/token" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# Run the batch job for the current month
curl -s -X POST "$BASE/admin/statements/run" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"month":"2026-10"}'
# {"jobExecutionId":..., "status":"COMPLETED", "exitCode":"COMPLETED", "statementsWritten": N}
```

Then confirm it's really in S3 -- not just that the API said so:

```bash
aws s3 ls "s3://$STATEMENTS_BUCKET/statements/" --recursive
```

You should see one `.csv` per account that had any ledger activity that month. Fetch one back
through the app itself (same path the admin console would use) to confirm round-tripping works:

```bash
curl -s "$BASE/admin/statements/<an-account-id>?month=2026-10" -H "Authorization: Bearer $TOKEN"
```

## 3. Confirm logs are really in CloudWatch

```bash
aws logs tail /ledgerguard/ledger-service --region us-east-1 --follow
```

You should see the same Spring Boot startup/request logs you'd see with `docker logs` on the
instance -- proving the `awslogs` driver is actually shipping them off-box, not just writing
locally.

## 4. Three networking experiments worth being able to explain

These are deliberate, reversible changes to prove you understand *why* each piece of the network
path matters, not just that it works. Undo each one after testing it.

### a. Remove the `0.0.0.0/0` route, watch the instance go unreachable

In the VPC console, edit `ledgerguard-public-rt` and delete the `0.0.0.0/0 -> igw` route.
`curl http://<IP>:8080/actuator/health` from your Mac now times out completely -- not refused,
not an error, just silence, because there's no longer a path back out of the VPC to the public
internet for the subnet to be "public" at all. Put the route back and it works again immediately
(no instance restart needed -- routing is evaluated per-packet, not cached).

### b. Remove port 8080 from the security group: timeout vs. refused

Remove the port-8080 rule from `ledgerguard-sg` (keep the route table intact this time).
`curl http://<IP>:8080/...` now also **times out** -- a security group is a stateful firewall
that silently drops disallowed packets rather than rejecting them, so the client gets no
response at all and just waits.

Contrast this with stopping the Docker container (`docker stop ledger-service`) while the
security group rule is still in place: now the TCP SYN reaches the instance's kernel, finds
nothing listening on 8080, and the OS immediately sends back a TCP RST -- `curl` fails instantly
with "Connection refused" instead of hanging. Same symptom from the browser's perspective
("can't connect"), but a completely different failure mode and different place in the stack --
this distinction (dropped at the network layer vs. rejected at the OS/socket layer) is exactly
the kind of thing worth being able to explain precisely in an interview rather than just saying
"it didn't work."

### b (restore). `docker start ledger-service` and re-add the security group rule before moving on.

### c. `dig`/`traceroute` from the instance to Neon

SSH into the instance and run:

```bash
dig +short <your-neon-host>
traceroute <your-neon-host>
```

`dig` resolves Neon's hostname to its current IP (Neon's serverless Postgres sits behind a
hostname that can resolve to different IPs over time, unlike a fixed on-prem database server --
worth noting if asked why the app connects by hostname, never a hardcoded IP). `traceroute`
shows the actual hop-by-hop path leaving the VPC through the internet gateway, out to the public
internet, and into Neon's infrastructure -- a concrete, visible demonstration that this traffic
really does leave AWS entirely (Neon isn't inside this VPC, isn't peered, isn't a NAT target --
it's just another service on the public internet that the security group's outbound rule and the
route table's `0.0.0.0/0` route both have to cooperate to reach).
