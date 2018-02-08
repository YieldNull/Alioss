对于`自签名`与`STS授权`两种方式而言，都需要搭建一个`HTTP Server`用来管理授权。下面采用`Python Flask`来搭建一个授权服务器

```python
# coding=utf-8

"""
OSS 访问控制
https://help.aliyun.com/document_detail/32046.html

Created by YieldNull on 10/14/16.
"""

import uuid
import datetime
import collections
import urllib
import hashlib
import hmac
import base64
import requests
import ConfigParser
import os

from flask import Flask, request

app = Flask(__name__)

parser = ConfigParser.ConfigParser()
parser.readfp(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'access.conf')))

sts_key = parser.get('sts', 'key')
sts_secret = parser.get('sts', 'secret')
sts_role = parser.get('sts', 'role')

custom_key = parser.get('custom', 'key')
custom_secret = parser.get('custom', 'secret')


@app.route('/auth/sts')
def auth_sts():
    """
    通过阿里STS服务器签名
    https://help.aliyun.com/document_detail/28763.html
    :return: STS服务器返回的JSON字符串。
    """
    role_name = request.args.get('roleName')
    if not role_name:
        return '403 Forbidden', 403

    args = {
        'Format': 'JSON',
        'Version': '2015-04-01',
        'SignatureMethod': 'HMAC-SHA1',
        'SignatureNonce': str(uuid.uuid4()),
        'SignatureVersion': '1.0',
        'AccessKeyId': sts_key,
        'Timestamp': datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ'),
        'RoleArn': sts_role,
        'RoleSessionName': role_name,
        'Action': 'AssumeRole'
    }

    args['Signature'] = _sign_url(args)

    return requests.get('https://sts.aliyuncs.com', params=args).text


@app.route('/auth/custom', methods=['POST'])
def auth_custom():
    """
    自签名
    :return:
    """
    content = request.form.get('content')
    if not content:
        return '403 Forbidden', 403

    return 'OSS {:s}:{:s}'.format(custom_key, _sign(custom_secret, content))


def _sign(secret, content):
    """
    生成签名
    :param secret: 签名使用的密钥
    :param content:要签名的内容
    :return:
    """

    h = hmac.new(secret, content, hashlib.sha1)
    return base64.encodestring(h.digest()).strip()


def _sign_url(args):
    """
    根据url中的query string签名
    :param args: query string(未被转义)
    :return:
    """

    query_str = ''
    for key in collections.OrderedDict(sorted(args.items())):
        # urllib.quote 默认对 '/'不转义，故要更改默认值
        # url中的特殊字符会先被转义,然后再根据转义后的query string计算，故要quote两次
        query_str += key + urllib.quote('=' + urllib.quote(args[key], safe='~') + '&', safe='~')

    # 删除末尾被转义的&
    query_str = query_str[:-3]

    str_to_sign = 'GET&{:s}&{:s}'.format(urllib.quote('/', safe='~'), query_str)
    return _sign(sts_secret + '&', str_to_sign)


if __name__ == '__main__':
    app.run(debug=True)

```

### access.conf

其中`access.conf`是`access_key_id`与`access_key_secret`的存储之处。

若使用自签名方式，请在该文件中写入：

```ini
[custom]
key=your_access_key_id
secret=your_access_key_secret
```

若使用STS服务，请在该文件中写入：

```ini
[sts]
key=your_access_key_id
secret=your_access_key_secret
role=your_role_arn
```

关于`roleArn`，参见[授权配置](授权配置)