package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送手机验证码
     */
    public Result sendCode(String phone, HttpSession session) {

        //1.检验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //2.1不符合，返回
            return Result.fail("手机号格式错误！");
        }
        //2.2 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.将验证码保存进session
        session.setAttribute("code",code);
        //4.发送验证码
        log.debug("发送验证码成功，验证码：{}",code);
        //5.返回ok
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            //不符合，返回
            return Result.fail("手机号格式错误！");
        }

        //2.校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(code == null || !cacheCode.toString().equals(code)){
            //3.不一致，报错
            return Result.fail("验证码错误!");
        }

        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        if (user == null){
            //5。不存在，创建新用户并保存
            user = createWithPhone(phone);
        }

        //6.保存用户数据到session中
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        session.setAttribute("user", userDTO);
        return Result.ok();
    }

    private User createWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
