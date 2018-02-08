根据阿里云文档OSS[访问控制章节](https://help.aliyun.com/document_detail/32046.html)所述，存在以下三种授权方式：

1. 明文设置：在客户端存储`access_key_id`以及`access_key_secret`
2. 自签名：服务器端存储`access_key_id`以及`access_key_secret`，客户端的所有请求都要先经过服务器签名
3. 使用STS服务：服务器端存储`access_key_id`以及`access_key_secret`，服务器端访问STS服务，得到`stsToken`。客户端从服务器端获取`stsToken`，获取临时的访问权限。客户端持有`stsToken`即可直接访问OSS服务。`stsToken`一段时间后过期，需要从服务器端获取更新。

为了避免泄露`access_key_secret`，不推荐使用第一种方式；使用第二种方式时，客户端的所有OSS请求都要先经过服务器；使用第三种方式时，只需要在`stsToken`过期时再访问服务器，同时在向STS服务请求`stsToken`时可以附加`policy`来进一步限制临时权限。推荐使用第三种方式。


## 1. 使用明文密钥

创建一个拥有对指定`bucket`访问权限的`RAM`用户，比如将系统策略`AliyunOSSFullAccess`赋予之。同时生成`access_key`。将得到的`access_key_id`及`access_key_secret`直接在客户端使用即可。

## 2. 使用自签名

创建一个拥有对指定`bucket`访问权限的`RAM`用户，比如将系统策略`AliyunOSSFullAccess`赋予之。同时生成`access_key`。将得到的`access_key_id`及`access_key_secret`存储在服务器端，在服务器端部署一个`HTTP Server`，用来签名客户端请求。

## 3. 使用STS进行授权

需要创建一个`RAM`用户，将`RAM`用户的`access_key_id`及`access_key_secret`存储在服务器端，在服务器端部署一个`HTTP Server`，用来向STS服务请求`stsToken`，并发放给客户端。

请求`stsToken`时，该用户要临时扮演一个指定`bucket`使用者的角色（需要创建）。同时要创建两个授权策略，一个赋予角色（使之能访问bucket），一个赋予用户(使之能扮演角色)。STS服务会让该用户临时扮演该角色，则该用户获得了该角色的权限，即可以临时访问bucket。配置方式如下：

### 3.1 自定义授权策略：访问bucket

自定义一个对指定`bucket`拥有所有权限的策略，假设名为`AliyunOSSFullAccess-bucket-name`。创建时，使用系统模板`AliyunOSSFullAccess`

```json
{
  "Statement": [
    {
      "Action": "oss:*",
      "Effect": "Allow",
      "Resource": [
        "acs:oss:*:*:bucket-name",
        "acs:oss:*:*:bucket-name/*"
      ]
    }
  ],
  "Version": "1"
}
```

其中`Action`表示权限，可以自定义成所需权限。详见[阿里云文档](https://help.aliyun.com/document_detail/28663.html)

### 3.2 创建RAM-Role：访问bucket

创建一个**用户角色**，假设名为`oss-bucket-name`，并把授权策略`AliyunOSSFullAccess-bucket-name`赋予之，使之拥有对`bucket-name`的所有权限。详见[阿里云文档](https://help.aliyun.com/document_detail/28649.html)


### 3.3 自定义授权策略：扮演角色

用户扮演角色也是需要授权的，因此要给用户授予扮演角色的权限。详见[阿里云文档](https://help.aliyun.com/document_detail/31935.html)

假设名为`AliyunSTSAssumeRoleAccess-bucket-name`，创建时使用系统模板`AliyunSTSAssumeRoleAccess`

```json
{
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Effect": "Allow",
      "Resource": "acs:ram::1326723194111613:role/oss-bucket-name"
    }
  ],
  "Version": "1"
}
```

其中`acs:ram::1326723194111613:role/oss-bucket-name`为`roleArn`，该值会在服务器端使用。

### 3.4 创建RAM-User

创建一个RAM用户，来扮演角色`AliyunOSSFullAccess-bucket-name`。新建时，选择生成`access_key`，同时将授权策略`AliyunSTSAssumeRoleAccess-bucket-name`授予之。


