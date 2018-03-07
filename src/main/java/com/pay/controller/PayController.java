package com.pay.controller;

import cn.exrick.bean.Pay;
import cn.exrick.bean.dto.DataTablesResult;
import cn.exrick.bean.dto.Result;
import cn.exrick.common.utils.*;
import cn.exrick.service.PayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Exrickx
 */
@Controller
public class PayController {

    private static final Logger log= LoggerFactory.getLogger(PayController.class);

    @Autowired
    private PayService payService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private EmailUtils emailUtils;

    @Value("${ip.expire}")
    private Long IP_EXPIRE;

    @Value("${my.token}")
    private String MY_TOKEN;

    @Value("${email.sender}")
    private String EMAIL_SENDER;

    @Value("${email.receiver}")
    private String EMAIL_RECEIVER;

    @Value("${token.admin.expire}")
    private Long ADMIN_EXPIRE;

    @Value("${server.url}")
    private String SERVER_URL;

    @RequestMapping(value = "/thanks/list",method = RequestMethod.GET)//获取捐赠列表
    @ResponseBody
    public DataTablesResult getThanksList(){

        DataTablesResult result=new DataTablesResult();
        List<Pay> list=new ArrayList<>();
        try {
            list=payService.getPayList(1);

        }catch (Exception e){
            result.setSuccess(false);
            result.setError("获取捐赠列表失败");
            return result;
        }
        result.setData(list);
        result.setSuccess(true);
        return result;
    }

    @RequestMapping(value = "/pay/list",method = RequestMethod.GET) //获取未支付列表
    @ResponseBody
    public DataTablesResult getPayList(){

        DataTablesResult result=new DataTablesResult();
        List<Pay> list=new ArrayList<>();
        try {
            list=payService.getNotPayList();
        }catch (Exception e){
            result.setSuccess(false);
            result.setError("获取未支付数据失败");
            return result;
        }
        result.setData(list);
        result.setSuccess(true);
        return result;
    }

    @RequestMapping(value = "/pay/check/list",method = RequestMethod.GET) //获取支付审核列表
    @ResponseBody
    public DataTablesResult getCheckList(){

        DataTablesResult result=new DataTablesResult();
        List<Pay> list=new ArrayList<>();
        try {
            list=payService.getPayList(0);
        }catch (Exception e){
            result.setSuccess(false);
            result.setError("获取支付审核列表失败");
            return result;
        }
        result.setData(list);
        result.setSuccess(true);
        return result;
    }

    @RequestMapping(value = "/pay/{id}",method = RequestMethod.GET)	//获取支付数据
    @ResponseBody
    public Result<Object> getPayList(@PathVariable String id,
                                     @RequestParam(required = true) String token){

        String temp=redisUtils.get(id);
        if(!token.equals(temp)){
            return new ResultUtil<Object>().setErrorMsg("无效的Token或链接");
        }
        Pay pay=null;
        try {
            pay=payService.getPay(getPayId(id));
        }catch (Exception e){
            return new ResultUtil<Object>().setErrorMsg("获取支付数据失败");
        }
        return new ResultUtil<Object>().setData(pay);
    }

    @RequestMapping(value = "/pay/add",method = RequestMethod.POST) //添加支付订单
    @ResponseBody
    public Result<Object> addPay(@ModelAttribute Pay pay, HttpServletRequest request){

        if(StringUtils.isBlank(pay.getNickName())||StringUtils.isBlank(String.valueOf(pay.getMoney()))
                ||StringUtils.isBlank(pay.getEmail())||!EmailUtils.checkEmail(pay.getEmail())){
            return new ResultUtil<Object>().setErrorMsg("请填写完整信息和正确的通知邮箱");
        }
        //防炸库验证
        String ip= IpInfoUtils.getIpAddr(request);
        if("0:0:0:0:0:0:0:1".equals(ip)){
            ip="127.0.0.1";
        }
        String temp=redisUtils.get(ip);
        if(StringUtils.isNotBlank(temp)){
            return new ResultUtil<Object>().setErrorMsg("您提交的太频繁，服务器繁忙！请2分钟后再试");
        }
        try {
            payService.addPay(pay);
            pay.setTime(StringUtils.getTimeStamp(new Date()));
        }catch (Exception e){
            return new ResultUtil<Object>().setErrorMsg("添加捐赠支付订单失败");
        }
        //记录缓存
        redisUtils.set(ip,"added",IP_EXPIRE, TimeUnit.MINUTES);

        //给管理员发送审核邮件
        String tokenAdmin= UUID.randomUUID().toString();
        redisUtils.set(pay.getId(),tokenAdmin,ADMIN_EXPIRE,TimeUnit.DAYS);
        pay=getAdminUrl(pay,pay.getId(),tokenAdmin,MY_TOKEN);
        emailUtils.sendTemplateMail(EMAIL_SENDER,EMAIL_RECEIVER,"【个人收款支付系统】待审核处理","email-admin",pay);

        return new ResultUtil<Object>().setData(null);
    }

    @RequestMapping(value = "/pay/edit",method = RequestMethod.POST) //编辑支付订单
    @ResponseBody
    public Result<Object> editPay(@ModelAttribute Pay pay,
                                  @RequestParam(required = true) String id,
                                  @RequestParam(required = true) String token){

        String temp=redisUtils.get(id);
        if(!token.equals(temp)){
            return new ResultUtil<Object>().setErrorMsg("无效的Token或链接");
        }
        try {
            pay.setId(getPayId(pay.getId()));
            Pay p=payService.getPay(getPayId(pay.getId()));
            pay.setState(p.getState());
            pay.setCreateTime(StringUtils.getDate(pay.getTime()));
            payService.updatePay(pay);
        }catch (Exception e){
            return new ResultUtil<Object>().setErrorMsg("编辑支付订单失败");
        }
        return new ResultUtil<Object>().setData(null);
    }

    @RequestMapping(value = "/pay/pass",method = RequestMethod.GET) //审核通过支付订单
    public String addPay(@RequestParam(required = true) String id,
                         @RequestParam(required = true) String token,
                         @RequestParam(required = true) String myToken,
                         Model model){

        String temp=redisUtils.get(id);
        if(!token.equals(temp)){
            model.addAttribute("errorMsg","无效的Token或链接");
            return "/500";
        }
        if(!myToken.equals(MY_TOKEN)){
            model.addAttribute("errorMsg","您未通过二次验证!");
            return "/500";
        }
        try {
            payService.changePayState(getPayId(id),1);
            //通知回调
            Pay pay=payService.getPay(getPayId(id));
            if(StringUtils.isNotBlank(pay.getEmail())&&EmailUtils.checkEmail(pay.getEmail())){
                emailUtils.sendTemplateMail(EMAIL_SENDER,pay.getEmail(),"【个人收款支付系统】支付成功通知","pay-success",pay);
            }
        }catch (Exception e){
            model.addAttribute("errorMsg","处理数据出错");
            return "/500";
        }
        return "redirect:/success";
    }

    @RequestMapping(value = "/pay/passNotShow",method = RequestMethod.GET) //审核通过但不显示加入捐赠表
    public String passNotShowPay(@RequestParam(required = true) String id,
                                 @RequestParam(required = true) String token,
                                 Model model){

        String temp=redisUtils.get(id);
        if(!token.equals(temp)){
            model.addAttribute("errorMsg","无效的Token或链接");
            return "/500";
        }
        try {
            payService.changePayState(getPayId(id),3);
            //通知回调
            Pay pay=payService.getPay(getPayId(id));
            if(StringUtils.isNotBlank(pay.getEmail())&&EmailUtils.checkEmail(pay.getEmail())){
                emailUtils.sendTemplateMail(EMAIL_SENDER,pay.getEmail(),"【个人收款支付系统】支付成功通知","pay-notshow",pay);
            }
        }catch (Exception e){
            model.addAttribute("errorMsg","处理数据出错");
            return "/500";
        }
        return "redirect:/success";
    }


    @RequestMapping(value = "/pay/back",method = RequestMethod.GET) //审核驳回支付订单
    public String backPay(@RequestParam(required = true) String id,
                          @RequestParam(required = true) String token,
                          @RequestParam(required = true) String myToken,
                          Model model){

        String temp=redisUtils.get(id);
        if(!token.equals(temp)){
            model.addAttribute("errorMsg","无效的Token或链接");
            return "/500";
        }
        if(!myToken.equals(MY_TOKEN)){
            model.addAttribute("errorMsg","您未通过二次验证");
            return "/500";
        }
        try {
            payService.changePayState(getPayId(id),2);
            //通知回调
            Pay pay=payService.getPay(getPayId(id));
            if(StringUtils.isNotBlank(pay.getEmail())&&EmailUtils.checkEmail(pay.getEmail())){
                emailUtils.sendTemplateMail(EMAIL_SENDER,pay.getEmail(),"【个人收款支付系统】支付失败通知","pay-fail",pay);
            }
        }catch (Exception e){
            model.addAttribute("errorMsg","处理数据出错");
            return "/500";
        }
        return "redirect:/success";
    }

    @RequestMapping(value = "/pay/del",method = RequestMethod.GET) //删除订单
    @ResponseBody
    public Result<Object> delPay(@RequestParam(required = true) String id,
                         @RequestParam(required = true) String token){

        String temp=redisUtils.get(id);
        if(!token.equals(temp)){
            return new ResultUtil<Object>().setErrorMsg("无效的Token或链接");
        }
        try {
            //通知回调
            Pay pay=payService.getPay(getPayId(id));
            if(StringUtils.isNotBlank(pay.getEmail())&&EmailUtils.checkEmail(pay.getEmail())){
                emailUtils.sendTemplateMail(EMAIL_SENDER,pay.getEmail(),"【个人收款支付系统】支付失败通知","pay-fail",pay);
            }
            payService.delPay(getPayId(id));
        }catch (Exception e){
            log.error(e.getMessage());
            return new ResultUtil<Object>().setErrorMsg("删除支付订单失败");
        }
  
        return new ResultUtil<Object>().setData(null);
    }

    /**
     * 拼接管理员链接
     */
    public Pay getAdminUrl(Pay pay,String id,String token,String myToken){

        String pass=SERVER_URL+"/pay/pass?id="+id+"&token="+token+"&myToken="+myToken;
        pay.setPassUrl(pass);

        String back=SERVER_URL+"/pay/back?id="+id+"&token="+token+"&myToken="+myToken;
        pay.setBackUrl(back);

        String passNotShow=SERVER_URL+"/pay/passNotShow?id="+id+"&token="+token;
        pay.setPassNotShowUrl(passNotShow);

        String edit=SERVER_URL+"/pay-edit?id="+id+"&token="+token;
        pay.setEditUrl(edit);

        String del=SERVER_URL+"/pay-del?id="+id+"&token="+token;
        pay.setDelUrl(del);
        return pay;
    }
    public String getPayId(String id){
        return id;
    }
}
