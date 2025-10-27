'''
Do these in your terminal
export SECRET=<Get it from the environment>
export ISSUER='ai-antfarm-dev'
export TENANT='tenant-local'
export USER='admin'
export BASE='http://localhost:9000'
'''

import jwt, time, os
secret=os.environ["SECRET"]
iss=os.environ.get("ISSUER","ai-antfarm-dev")
tenant=os.environ.get("TENANT","tenant-local")
sub=os.environ.get("USER","admin")
now=int(time.time())
payload={"iss":iss,"sub":sub,"tenantId":tenant,"iat":now,"exp":now+3600}
print(jwt.encode(payload, secret, algorithm="HS256"))
