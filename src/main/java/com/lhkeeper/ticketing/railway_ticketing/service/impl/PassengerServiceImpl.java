package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.context.UserInfo;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PassengerRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.PassengerService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PassengerServiceImpl extends ServiceImpl<PassengerMapper, Passenger>
        implements PassengerService {

    private final PassengerMapper passengerMapper;
    private final AbstractChainContext<PassengerCreateReqDTO> passengerCreateChainContext;
    private final AbstractChainContext<PassengerUpdateReqDTO> passengerUpdateChainContext;
    private final AbstractChainContext<Long> passengerDeleteChainContext;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PassengerRespDTO create(PassengerCreateReqDTO reqDTO) {
        passengerCreateChainContext.handler(ChainMarkEnum.PASSENGER_CREATE.name(), reqDTO);

        UserInfo currentUser = UserContext.get();
        Passenger passenger = new Passenger();
        passenger.setUserId(currentUser.getUserId());
        passenger.setUsername(currentUser.getUsername());
        passenger.setRealName(reqDTO.getRealName());
        passenger.setIdType(reqDTO.getIdType());
        passenger.setIdCard(reqDTO.getIdCard());
        passenger.setPhone(reqDTO.getPhone());
        passenger.setDiscountType(reqDTO.getDiscountType() != null ? reqDTO.getDiscountType() : 0);
        passenger.setCreateDate(LocalDateTime.now());
        passenger.setVerifyStatus(0);

        passengerMapper.insert(passenger);

        Passenger saved = passengerMapper.selectById(passenger.getId());
        return toRespDTO(saved);
    }

    @Override
    public PassengerRespDTO update(Long passengerId, PassengerUpdateReqDTO reqDTO) {
        passengerUpdateChainContext.handler(ChainMarkEnum.PASSENGER_UPDATE.name(), reqDTO);

        Passenger passenger = passengerMapper.selectById(passengerId);
        if (passenger == null) {
            throw new ClientException("乘车人不存在");
        }
        Long currentUserId = UserContext.get().getUserId();
        if (!currentUserId.equals(passenger.getUserId())) {
            throw new ClientException("乘车人不存在或无权操作");
        }

        if (reqDTO.getPhone() != null) {
            passenger.setPhone(reqDTO.getPhone());
        }
        if (reqDTO.getDiscountType() != null) {
            passenger.setDiscountType(reqDTO.getDiscountType());
        }
        passengerMapper.updateById(passenger);

        Passenger updated = passengerMapper.selectById(passengerId);
        return toRespDTO(updated);
    }

    @Override
    public void delete(Long passengerId) {
        passengerDeleteChainContext.handler(ChainMarkEnum.PASSENGER_DELETE.name(), passengerId);

        passengerMapper.deleteById(passengerId);
    }

    @Override
    public List<PassengerRespDTO> listMine() {
        Long userId = UserContext.get().getUserId();
        List<Passenger> passengers = passengerMapper.selectList(
                Wrappers.lambdaQuery(Passenger.class)
                        .eq(Passenger::getUserId, userId)
                        .orderByAsc(Passenger::getCreateDate)
        );
        return passengers.stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
    }

    private PassengerRespDTO toRespDTO(Passenger p) {
        return PassengerRespDTO.builder()
                .id(p.getId())
                .realName(p.getRealName())
                .idType(p.getIdType())
                .idCard(desensitizeIdCard(p.getIdCard()))
                .phone(desensitizePhone(p.getPhone()))
                .discountType(p.getDiscountType())
                .verifyStatus(p.getVerifyStatus())
                .createDate(p.getCreateDate() != null
                        ? p.getCreateDate().format(DATE_FORMATTER) : null)
                .build();
    }

    private String desensitizeIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 3) + "***********" + idCard.substring(idCard.length() - 4);
    }

    private String desensitizePhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
