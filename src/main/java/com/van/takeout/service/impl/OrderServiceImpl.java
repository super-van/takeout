package com.van.takeout.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.van.takeout.dao.OrderDao;
import com.van.takeout.entity.*;
import com.van.takeout.exception.CustomException;
import com.van.takeout.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderDao, Order> implements OrderService {
    @Autowired
    private ShoppingCartService shoppingCartService;
    @Autowired
    private UserService userService;
    @Autowired
    private AddressBookService addressBookService;
    @Autowired
    private OrderDetailService orderDetailService;

    @Override
    @Transactional
    public void submit(Order order, Long userId) {
        //当前用户的购物车数据，防小人，可能为null，防小人的工作海了去了，都没搞
        List<ShoppingCart> shoppingCarts = shoppingCartService.listByUserId(userId);
        if (shoppingCarts == null) {
            throw new CustomException("购物车为空");
        }
        //用户数据
        User user = userService.getById(userId);
        order.setUserId(userId);
        order.setUserName(user.getName());
        //地址数据，若调用copyProperties则注意属性名冲突
        AddressBook addressBook = addressBookService.getById(order.getAddressBookId());
        order.setAddressBookId(order.getAddressBookId());
        order.setAddress(
                (addressBook.getProvinceName() == null ? "" : addressBook.getProvinceName())
                        + (addressBook.getCityName() == null ? "" : addressBook.getCityName())
                        + (addressBook.getDistrictName() == null ? "" : addressBook.getDistrictName())
                        + addressBook.getDetail()
        );
        order.setPhone(addressBook.getPhone());
        order.setConsignee(addressBook.getConsignee());
        //向订单表插入一条
        order.setNumber(String.valueOf(IdWorker.getId()));
        order.setOrderTime(LocalDateTime.now());
        order.setCheckoutTime(LocalDateTime.now());//没有做支付，时间凑一起了
        Optional<BigDecimal> amount = shoppingCarts.stream().map(shoppingCart -> new BigDecimal(String.valueOf(shoppingCart.getNumber())).multiply(shoppingCart.getAmount())).reduce(BigDecimal::add);
        order.setAmount(amount.get());
        save(order);
        //向订单明细表插入一条或多条
        List<OrderDetail> orderDetails = shoppingCarts.stream().map(shoppingCart -> {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart, orderDetail);
            orderDetail.setOrderId(order.getId());
            return orderDetail;
        }).collect(Collectors.toList());
        orderDetailService.saveBatch(orderDetails);
        //从购物车表删除相应记录
        shoppingCartService.removeByUserId(userId);
    }

    @Override
    public Page<OrderDto> pageByUserId(int page, int pageSize, Object userId) {
        Page<Order> orderPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getUserId, userId).orderByDesc(Order::getOrderTime);
        LambdaQueryWrapper<OrderDetail> orderDetailQuery = new LambdaQueryWrapper<>();
        List<OrderDto> orderDtos = page(orderPage, queryWrapper).getRecords().stream().map(order -> {
            OrderDto orderDto = new OrderDto();
            BeanUtils.copyProperties(order, orderDto);
            //别忘了清空条件
            orderDetailQuery.clear();
            orderDetailQuery.eq(OrderDetail::getOrderId, order.getId());
            orderDto.setOrderDetails(orderDetailService.list(orderDetailQuery));
            return orderDto;
        }).collect(Collectors.toList());
        Page<OrderDto> orderDtoPage = new Page<>();
        BeanUtils.copyProperties(orderPage, orderDtoPage, "records");
        orderDtoPage.setRecords(orderDtos);
        return orderDtoPage;
    }

    @Override
    public Page<Order> pageByMany(int page, int pageSize, String number, LocalDateTime beginTime, LocalDateTime endTime) {
        Page<Order> pageData = new Page<>(page, pageSize);
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(number != null, Order::getNumber, number).between(beginTime != null && endTime != null, Order::getCheckoutTime, beginTime, endTime);
        return page(pageData, queryWrapper);
    }
}
