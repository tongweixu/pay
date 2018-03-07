# pay
开源网站捐赠支付系统<br>
功能：向开源网站，开源项目进行捐赠或可以用于个人支付系统<br>
* maven管理
* 后端主要技术:spring—boot,spring-data-jpa,spring-data-redis,spring-thymeleaf
* 前端主要技术:jQuery+bootstrap
* 服务器：tomcat<br>
## 只需修改src/main/resources/static/assets/qr 文件下的支付二维码即可 其他内容看代码内注释<br>
## 具体功能展示
捐赠者填写捐赠信息并支付 后台通过邮箱发送支付信息（支付成功或失败等）给捐赠者
![image](https://github.com/tongweixu/pay/raw/master/Readme_image/捕获1.PNG) 
![image](https://github.com/tongweixu/pay/raw/master/Readme_image/捕获2.PNG) 
<br>
捐赠者提交支付时，管理员收到审核邮件，审核支付情况和修改支付信息
![image](https://github.com/tongweixu/pay/raw/master/Readme_image/捕获3.PNG) 
![image](https://github.com/tongweixu/pay/raw/master/Readme_image/捕获4.PNG) 
<br>
各捐赠者捐赠情况
![image](https://github.com/tongweixu/pay/raw/master/Readme_image/捕获5.PNG) 



