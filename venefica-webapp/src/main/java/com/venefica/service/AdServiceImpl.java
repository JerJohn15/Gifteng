package com.venefica.service;

import com.venefica.config.Constants;
import com.venefica.dao.AdDataDao;
import com.venefica.dao.ApprovalDao;
import com.venefica.dao.BookmarkDao;
import com.venefica.dao.CategoryDao;
import com.venefica.dao.CommentDao;
import com.venefica.dao.ImageDao;
import com.venefica.dao.MessageDao;
import com.venefica.dao.RatingDao;
import com.venefica.dao.SpamMarkDao;
import com.venefica.dao.UserPointDao;
import com.venefica.dao.UserTransactionDao;
import com.venefica.dao.ViewerDao;
import com.venefica.model.Ad;
import com.venefica.model.AdStatus;
import com.venefica.model.AdType;
import com.venefica.model.Approval;
import com.venefica.model.Bookmark;
import com.venefica.model.BusinessAdData;
import com.venefica.model.Category;
import com.venefica.model.Comment;
import com.venefica.model.Image;
import com.venefica.model.Message;
import com.venefica.model.NotificationType;
import com.venefica.model.Rating;
import com.venefica.model.Request;
import com.venefica.model.RequestStatus;
import com.venefica.model.SpamMark;
import com.venefica.model.User;
import com.venefica.model.UserPoint;
import com.venefica.model.UserTransaction;
import com.venefica.model.Viewer;
import com.venefica.service.dto.AdDto;
import com.venefica.service.dto.AdStatisticsDto;
import com.venefica.service.dto.ApprovalDto;
import com.venefica.service.dto.CategoryDto;
import com.venefica.service.dto.FilterDto;
import com.venefica.service.dto.ImageDto;
import com.venefica.service.dto.RatingDto;
import com.venefica.service.dto.RequestDto;
import com.venefica.service.dto.builder.AdDtoBuilder;
import com.venefica.service.fault.AdField;
import com.venefica.service.fault.AdNotFoundException;
import com.venefica.service.fault.AdValidationException;
import com.venefica.service.fault.AlreadyRatedException;
import com.venefica.service.fault.AlreadyRequestedException;
import com.venefica.service.fault.ApprovalNotFoundException;
import com.venefica.service.fault.AuthorizationException;
import com.venefica.service.fault.BookmarkNotFoundException;
import com.venefica.service.fault.GeneralException;
import com.venefica.service.fault.ImageField;
import com.venefica.service.fault.ImageNotFoundException;
import com.venefica.service.fault.ImageValidationException;
import com.venefica.service.fault.InvalidAdStateException;
import com.venefica.service.fault.InvalidRateOperationException;
import com.venefica.service.fault.InvalidRequestException;
import com.venefica.service.fault.RequestNotFoundException;
import com.venefica.service.fault.UserNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.jws.WebService;
import org.apache.commons.lang.time.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("adService")
@WebService(endpointInterface = "com.venefica.service.AdService")
public class AdServiceImpl extends AbstractService implements AdService {

    @Inject
    private ApprovalDao approvalDao;
    @Inject
    private AdDataDao adDataDao;
    @Inject
    private CategoryDao categoryDao;
    @Inject
    private ImageDao imageDao;
    @Inject
    private BookmarkDao bookmarkDao;
    @Inject
    private SpamMarkDao spamMarkDao;
    @Inject
    private RatingDao ratingDao;
    @Inject
    private CommentDao commentDao;
    @Inject
    private MessageDao messageDao;
    @Inject
    private ViewerDao viewerDao;
    @Inject
    private UserPointDao userPointDao;
    @Inject
    private UserTransactionDao userTransactionDao;

    
    
    //**********************
    //* categories related *
    //**********************
    
    @Override
    public List<CategoryDto> getCategories(Long categoryId) {
        return getCategoriesInternal(categoryId, false);
    }

    @Override
    @Transactional
    public List<CategoryDto> getAllCategories() {
        return getCategoriesInternal(null, true);
    }
    
    

    //***********************************
    //* ad cruds (create/update/delete) *
    //***********************************
    
    @Override
    @Transactional
    public Long placeAd(AdDto adDto) throws AdValidationException {
        // ++ TODO: create ad validator
        if (adDto.getTitle() == null) {
            throw new AdValidationException(AdField.TITLE, "Title not specified!");
        }
        
        Category category = validateCategory(adDto.getCategoryId());
        Image image = saveImage(adDto.getImage(), AdField.IMAGE); //main image
        Image thumbImage = saveImage(adDto.getImageThumbnail(), AdField.THUMB_IMAGE); //thumb image
        Image barcodeImage = saveImage(adDto.getImageBarcode(), AdField.BARCODE_IMAGE); //barcode image
        // ++
        
        User currentUser = getCurrentUser();
//        BigDecimal beforePendingNumber = null;
        
//        if ( !currentUser.isBusinessAccount() ) {
//            beforePendingNumber = calculatePendingNumber(currentUser);
//        }
        
        boolean expires = adDto.getExpires() != null ? adDto.getExpires() : true;
        Date expiresAt = adDto.getExpiresAt() != null ? adDto.getExpiresAt() : DateUtils.addDays(new Date(), Constants.AD_EXPIRATION_PERIOD_DAYS);
        Date availableAt = adDto.getAvailableAt() != null ? adDto.getAvailableAt() : new Date();
        
        Ad ad = new Ad(currentUser.isBusinessAccount() ? AdType.BUSINESS : AdType.MEMBER);
        ad.initRevision();
        adDto.updateAd(ad);
        
        ad.getAdData().setCategory(category);
        ad.getAdData().setMainImage(image);
        ad.getAdData().setThumbImage(thumbImage);
        
        if ( ad.getType() == AdType.BUSINESS ) {
            ((BusinessAdData) ad.getAdData()).setBarcodeImage(barcodeImage);
        }
        
        adDataDao.save(ad.getAdData());
        
        ad.setStatus(AdStatus.OFFLINE);
        ad.setCreator(currentUser);
        ad.setExpires(expires);
        ad.setExpired(false);
        ad.setExpiresAt(expiresAt);
        ad.setAvailableAt(availableAt);
        Long adId = adDao.save(ad);
        
        if ( !currentUser.isBusinessAccount() ) {
//            BigDecimal currentPendingNumber = calculatePendingNumber(currentUser);
//            BigDecimal pendingDelta = currentPendingNumber.subtract(beforePendingNumber);

            UserTransaction transaction = new UserTransaction(ad);
            transaction.setUser(currentUser);
            transaction.setUserPoint(currentUser.getUserPoint());
//            transaction.setPendingNumber(currentPendingNumber);
//            transaction.setPendingScore(pendingDelta.signum() > 0 ? pendingDelta : BigDecimal.ZERO);
            userTransactionDao.save(transaction);
        }
        
        //all followers should be notified (if configured) about the new ad
        Set<User> followers = currentUser.getFollowers();
        if ( followers != null && !followers.isEmpty() ) {
            for ( User follower : followers ) {
                Map<String, Object> vars = new HashMap<String, Object>(0);
                vars.put("ad", ad);
                vars.put("follower", follower);
                
                emailSender.sendNotification(NotificationType.FOLLOWER_AD_CREATED, follower, vars);
            }
        }
        
        return adId;
    }
    
    @Override
    @Transactional
    public void updateAd(AdDto adDto) throws AdNotFoundException, AdValidationException, AuthorizationException {
        Ad ad = validateAd(adDto.getId());
        
        // ++ TODO: create ad validator
        if (adDto.getTitle() == null) {
            throw new AdValidationException(AdField.TITLE, "Title is null!");
        }

        Category category = validateCategory(adDto.getCategoryId());
        User currentUser = getCurrentUser();

        if (!ad.getCreator().equals(currentUser)) {
            throw new AuthorizationException("You can update only your own ads!");
        }

        // WARNING: This update must be performed within an active transaction!
        adDto.updateAd(ad);

        ad.incrementRevision();
        ad.setStatus(AdStatus.OFFLINE);
        ad.getAdData().setCategory(category);

        try {
            if (adDto.getImage() != null) {
                ImageDto imageDto = adDto.getImage();
                if (imageDto.isValid()) {
                    Image image = ad.getAdData().getMainImage();

                    if (image != null) {
                        ad.getAdData().setMainImage(null);
                        imageDao.delete(image);
                    }

                    image = imageDto.toImage();
                    imageDao.save(image);
                    ad.getAdData().setMainImage(image);
                } else if ( imageDto.getUrl() == null || imageDto.getUrl().trim().isEmpty() ) {
                    throw new AdValidationException(AdField.IMAGE, "Invalid image specified!");
                }
            }
        } catch ( IOException ex ) {
            logger.error("Exception when saving ad main image", ex);
        }

        try {
            if (adDto.getImageThumbnail() != null) {
                ImageDto thumbImageDto = adDto.getImageThumbnail();
                if (thumbImageDto.isValid()) {
                    Image thumbImage = ad.getAdData().getThumbImage();

                    if (thumbImage != null) {
                        ad.getAdData().setThumbImage(null);
                        imageDao.delete(thumbImage);
                    }

                    thumbImage = thumbImageDto.toImage();
                    imageDao.save(thumbImage);
                    ad.getAdData().setThumbImage(thumbImage);
                } else if ( thumbImageDto.getUrl() == null || thumbImageDto.getUrl().trim().isEmpty() ) {
                    throw new AdValidationException(AdField.THUMB_IMAGE, "Invalid image specified!");
                }
            }
        } catch ( IOException ex ) {
            logger.error("Exception when saving ad thumbnail image", ex);
        }
        
        try {
            if (ad.getType() == AdType.BUSINESS && adDto.getImageBarcode() != null) {
                ImageDto barcodeImageDto = adDto.getImageBarcode();
                if (barcodeImageDto.isValid()) {
                    Image barcodeImage = ((BusinessAdData) ad.getAdData()).getBarcodeImage();

                    if (barcodeImage != null) {
                        ad.getAdData().setThumbImage(null);
                        imageDao.delete(barcodeImage);
                    }

                    barcodeImage = barcodeImageDto.toImage();
                    imageDao.save(barcodeImage);
                    ((BusinessAdData) ad.getAdData()).setBarcodeImage(barcodeImage);
                } else if ( barcodeImageDto.getUrl() == null || barcodeImageDto.getUrl().trim().isEmpty() ) {
                    throw new AdValidationException(AdField.BARCODE_IMAGE, "Invalid image specified!");
                }
            }
        } catch ( IOException ex ) {
            logger.error("Exception when saving business ad barcode image", ex);
        }
        
        adDataDao.update(ad.getAdData());

//        if (adDto.getExpiresAt() != null && currentUser.isBusinessAcc()) {
//                ad.setExpiresAt(adDto.getExpiresAt());
//                ad.setExpired(false);
//        }

        // ++
    }
    
    @Override
    @Transactional
    public void deleteAd(Long adId) throws AdNotFoundException, AuthorizationException, InvalidAdStateException {
        Ad ad = validateAd(adId);
        User currentUser = getCurrentUser();
        UserTransaction transaction = userTransactionDao.getByAd(currentUser.getId(), adId);

        if (!ad.getCreator().equals(currentUser)) {
            throw new AuthorizationException("Only the creator can delete the ad");
        }
        if ( ad.isDeleted() ) {
            throw new InvalidAdStateException("Ad (adId: " + adId + ") is already deleted");
        }
        if ( transaction == null ) {
            throw new InvalidAdStateException("There is no transaction assiciated with this ad (adId: " + adId + ")");
        }

        ad.markAsDeleted();
        
        transaction.markAsFinalized();
        userTransactionDao.update(transaction);
    }

    @Override
    @Transactional
    public Long addImageToAd(Long adId, ImageDto imageDto) throws AdNotFoundException, ImageValidationException {
        Ad ad = validateAd(adId);

        // ++ TODO: create image validator
        if (imageDto == null) {
            throw new NullPointerException("imageDto is null");
        }
        if (imageDto.getImgType() == null) {
            throw new ImageValidationException(ImageField.IMAGE_TYPE, "Image type not specified!");
        }
        if (imageDto.getData() == null || imageDto.getData().length == 0) {
            throw new ImageValidationException(ImageField.DATA, "Image data must be not empty!");
        }
        // ++

        // Save and attache the image to the ad
        Image image = imageDto.toImage();
        try {
            imageDao.save(image);
        } catch ( IOException ex ) {
            logger.error("Exception when adding image to ad (adId: " + adId + ")", ex);
            throw new ImageValidationException(ImageField.DATA, "Image cannot be added to ad (adId: " + adId + ")");
        }
        ad.addImage(image);

        return image.getId();
    }

    @Override
    @Transactional
    public void deleteImageFromAd(Long adId, Long imageId) throws AdNotFoundException, AuthorizationException, ImageNotFoundException {
        deleteImagesFromAd(adId, Arrays.<Long>asList(imageId));
    }
    
    @Override
    @Transactional
    public void deleteImagesFromAd(Long adId, List<Long> imageIds) throws AdNotFoundException, AuthorizationException, ImageNotFoundException {
        if ( imageIds == null || imageIds.isEmpty() ) {
            logger.debug("Cannot delete empty list of images!");
            return;
        }
        
        Ad ad = validateAd(adId);
        User currentUser = getCurrentUser();

        if (!ad.getCreator().equals(currentUser)) {
            throw new AuthorizationException("Only creator can delete images!");
        }

        if ( imageIds.size() == 1 ) {
            Long imageId = imageIds.get(0);
            Image image = validateImage(imageId);
            
            if (!ad.getAdData().getImages().contains(image)) {
                throw new AuthorizationException("Image doesn't belong to the specified ad!");
            }
            
            try {
                imageDao.delete(image);
            } catch ( IOException ex ) {
                logger.error("Exception when removing image (imageId: " + imageId + ") from ad (adId: " + adId + ")", ex);
                throw new ImageNotFoundException("Image (imageId: " + imageId + ") cannot be removed");
            }
            
            ad.removeImage(image);
            adDataDao.update(ad.getAdData());
        } else {
            //some exceptions are not thrown, instead are captured and continued with the next image
            for ( Long imageId : imageIds ) {
                Image image;
                try {
                    image = validateImage(imageId);
                } catch ( ImageNotFoundException ex ) {
                    logger.warn("Cannot find image (imageId: " + imageId + ").");
                    continue;
                }

                if (!ad.getAdData().getImages().contains(image)) {
                    logger.warn("Image (imageId: " + imageId + ") doesn't belong to the specified ad!");
                    continue;
                }

                try {
                    imageDao.delete(image);
                    ad.removeImage(image);
                } catch ( IOException ex ) {
                    logger.error("Exception when removing image (imageId: " + imageId + ") from ad (adId: " + adId + ")", ex);
                }
            }
            adDataDao.update(ad.getAdData());
        }
    }

    
    
    //*********************
    //* approvals related *
    //*********************
    
    @Override
    public List<ApprovalDto> getApprovals(Long adId) throws AdNotFoundException, AuthorizationException {
        Ad ad = validateAd(adId);
        User currentUser = getCurrentUser();

        if (!ad.getCreator().equals(currentUser)) {
            throw new AuthorizationException("Only the creator can access approvals!");
        }
        
        List<ApprovalDto> result = new LinkedList<ApprovalDto>();
        List<Approval> approvals = approvalDao.getByAd(adId);
        
        for ( Approval approval : approvals ) {
            ApprovalDto approvalDto = new ApprovalDto(approval);
            result.add(approvalDto);
        }
        
        return result;
    }
    
    @Override
    public ApprovalDto getApproval(Long adId, Integer revision) throws AdNotFoundException, AuthorizationException, ApprovalNotFoundException {
        User currentUser = getCurrentUser();
        Ad ad = validateAd(adId);
        
        if (!ad.getCreator().equals(currentUser)) {
            throw new AuthorizationException("Only the creator can access approvals!");
        }
        
        Approval approval = approvalDao.getByAdAndRevision(adId, revision);
        if ( approval == null ) {
            throw new ApprovalNotFoundException(adId);
        }
        
        return new ApprovalDto(approval);
    }
    
    
    //*************************
    //* ads listing/filtering *
    //*************************
    
    @Override
    @Transactional
    public List<AdDto> getAds(Long lastAdId, int numberAds) {
        return getAds(lastAdId, numberAds, null);
    }

    @Override
    @Transactional
    public List<AdDto> getAds(Long lastAdId, int numberAds, FilterDto filter) {
        return getAds(lastAdId, numberAds, filter, false, false, 0);
    }
    
    @Override
    @Transactional
    public List<AdDto> getAds(Long lastAdId, int numberAds, FilterDto filter, Boolean includeImages, Boolean includeCreator, int includeCommentsNumber) {
        List<AdDto> result = new LinkedList<AdDto>();
        List<Ad> ads = adDao.get(lastAdId, numberAds, filter);

        User currentUser = getCurrentUser();

        // TODO: Optimize this
        // Get current user's bookmarks
        // TODO: REMOVE IT!!!!!!!!
        List<Ad> bokmarkedAds = bookmarkDao.getBookmarkedAds(currentUser);

        for (Ad ad : ads) {
            if ( !includeOwnedAd(ad, currentUser, filter) ) {
                //skipping owned ads
                continue;
            }
            
            List<Comment> comments = null;
            if ( includeCommentsNumber > 0 ) {
                comments = commentDao.getAdComments(ad.getId(), -1L, includeCommentsNumber);
            }
            
            AdDto adDto = new AdDtoBuilder(ad)
                    .setCurrentUser(currentUser)
                    .setFilteredComments(comments)
                    .includeImages(includeImages != null ? includeImages : false)
                    .includeCreator(includeCreator != null ? includeCreator : false)
                    .build();
            adDto.setInBookmarks(inBookmarks(bokmarkedAds, ad));
            adDto.setRequested(ad.isRequested(currentUser, false));
            
            result.add(adDto);
        }

        return result;
    }

    @Override
    @Transactional
    public List<AdDto> getMyAds(Boolean includeRequests) {
        try {
            Long userId = getCurrentUserId();
            return getUserAds(userId, includeRequests);
        } catch ( UserNotFoundException ex ) {
            logger.error("User (current/logged) not found", ex);
            return new ArrayList<AdDto>();
        }
    }
    
    @Override
    @Transactional
    public List<AdDto> getUserAds(Long userId, Boolean includeRequests) throws UserNotFoundException {
        User user = validateUser(userId);
        List<Ad> ads = adDao.getByUser(userId);
        List<AdDto> result = new LinkedList<AdDto>();
        
        if ( includeRequests == null ) {
            includeRequests = false;
        }
        
        for (Ad ad : ads) {
            AdDto adDto = new AdDtoBuilder(ad)
                    .setCurrentUser(user)
                    .includeRequests(includeRequests)
                    .includeCanRequest()
                    .build();
            
            if ( includeRequests ) {
                Approval approval = approvalDao.getByAdAndRevision(ad.getId(), ad.getRevision());
                if ( approval != null ) {
                    ApprovalDto approvalDto = new ApprovalDto(approval);
                    adDto.setApproval(approvalDto);
                }
            }
            
            result.add(adDto);
        }
        
        return result;
    }
    
    @Override
    @Transactional
    public List<AdDto> getUserRequestedAds(Long userId, Boolean includeRequests) throws UserNotFoundException {
        User currentUser = getCurrentUser();
        List<Request> requests = getActiveRequestsByUser(userId, false);
        List<AdDto> result = new LinkedList<AdDto>();
        
        if ( includeRequests == null ) {
            includeRequests = false;
        }
        
        for ( Request request : requests ) {
            Ad ad = request.getAd();
            
            // @formatter:off
            AdDto adDto = new AdDtoBuilder(ad)
                    .setCurrentUser(currentUser)
                    .includeCreator()
                    .includeRequests(includeRequests)
                    .includeCanRequest()
                    .build();
            // @formatter:on
            
            adDto.setInBookmarks(inBookmarks(currentUser, ad));
            adDto.setRequested(ad.isRequested(currentUser, false));
            
            result.add(adDto);
        }
        
        return result;
    }

    @Override
    @Transactional
    public AdDto getAdById(Long adId) throws AdNotFoundException, AuthorizationException {
        User currentUser = getCurrentUser();
        Ad ad = validateAd(adId);

        if ( !ad.isOnline() || !ad.isApproved() ) {
            if ( !currentUser.isAdmin() ) {
                throw new AuthorizationException("Ad (id: " + adId + ") is not yet available (only for admins)");
            }
        }
        
        if (!ad.getCreator().equals(currentUser) && !ad.isAlreadyViewedBy(currentUser)) {
            ad.visit();
        }
        
        Viewer viewer = new Viewer(ad, currentUser);
        viewerDao.save(viewer);

        // @formatter:off
        AdDto adDto = new AdDtoBuilder(ad)
                .setCurrentUser(currentUser)
                .includeImages()
                .includeCreator()
                .includeRequests()
                .includeCanMarkAsSpam()
                .includeCanRate()
                .includeCanRequest()
                .build();
        // @formatter:on

        adDto.setInBookmarks(inBookmarks(currentUser, ad));
        adDto.setRequested(ad.isRequested(currentUser, false));
        
        return adDto;
    }
    
    
    
    //*****************
    //* ad statistics *
    //*****************
    
    @Override
    @Transactional
    public AdStatisticsDto getStatistics(Long adId) throws AdNotFoundException {
        Ad ad = validateAd(adId);
        
        AdDto adDto = new AdDtoBuilder(ad).setCurrentUser(getCurrentUser()).build();
        return adDto.getStatistics();
    }

    
    
    //***********************
    //* ad lifecycle change *
    //***********************
    
    @Override
    @Transactional
    public void relistAd(Long adId) throws AdNotFoundException, AuthorizationException, InvalidAdStateException {
        Ad ad = validateAd(adId);
        User currentUser = getCurrentUser();

        if (!ad.getCreator().equals(currentUser)) {
            throw new AuthorizationException("Only the creator can relist the ad!");
        }
        if (ad.isDeleted() /* || ad.isSold() || ad.isSpam() */) {
            throw new InvalidAdStateException("Ad can't be relisted as is deleted!");
        }
        if ( !ad.isExpired() ) {
            throw new InvalidAdStateException("Ad can't be relisted as its state (" + ad.getStatus() + ") is not as expected!");
        }
        if ( !ad.canProlong() ) {
            throw new InvalidAdStateException("Ad can't be relisted anymore!");
        }
        
        //only EXPIRED ads can be relisted
        ad.prolong(Constants.AD_PROLONGATION_PERIOD_DAYS);
    }

    @Override
    @Transactional
    public void endAd(Long adId) throws AdNotFoundException, InvalidAdStateException, AuthorizationException {
        Ad ad = validateAd(adId);
        User currentUser = getCurrentUser();

        if (!ad.getCreator().equals(currentUser)) {
            throw new AuthorizationException("Only the creator can ends the ad");
        }
        if ( ad.isSold() ) {
            throw new InvalidAdStateException("The ad (adId: " + adId + ") is already ended/finalized");
        }

        ad.markAsSold();
        //ad.setExpired(true);
        //ad.setExpiresAt(new Date());
    }
    
    
    
    //***************
    //* ad requests *
    //***************
    
    @Override
    public void hideRequest(Long requestId) throws RequestNotFoundException, InvalidRequestException {
        Request request = validateRequest(requestId);
        User user = getCurrentUser();
        Ad ad = request.getAd();
        
        if ( !request.getUser().equals(user) ) {
            throw new InvalidRequestException("Only requestor can hide requests.");
        }
        if ( !ad.isExpired() && !request.isUnaccepted() && !request.isDeclined() ) {
            throw new InvalidRequestException("Only 'expired' (unaccepted or declined) requests (or expired ads) can be hidden.");
        }
        
        requestDao.hide(requestId);
    }
    
    @Override
    @Transactional
    public Long requestAd(Long adId, String text) throws AdNotFoundException, AlreadyRequestedException, InvalidRequestException, InvalidAdStateException {
        Ad ad = validateAd(adId);
        User user = getCurrentUser();
        Long userId = getCurrentUserId();
        User creator = ad.getCreator();
        
        if (ad.getStatus() == AdStatus.ACTIVE || ad.getStatus() == AdStatus.IN_PROGRESS) {
            //continue
        } else {
            //only ACTIVE or IN_PROGRESS status ads can be requested
            throw new InvalidAdStateException("Ad can't be requested as its state (" + ad.getStatus() + ") is not as expected!");
        }
        
        Request request = requestDao.get(userId, adId);
        if ( request != null ) {
            throw new AlreadyRequestedException("Ad (id: " + adId + ") already requested by the user (id: " + userId + ")");
        }
        
        if ( user.isBusinessAccount() ) {
            throw new InvalidRequestException("Business users cannot request ads.");
        }
        if ( creator.equals(user) ) {
            throw new InvalidRequestException("Cannot request owned ads.");
        }
        if ( ad.getAdData().getQuantity() <= 0 ) {
            throw new InvalidRequestException("No more available.");
        }
        if ( ad.getActiveRequests().size() >= Constants.REQUEST_MAX_ALLOWED ) {
            throw new InvalidRequestException("Max request limit reached.");
        }
        
//        BigDecimal beforePendingNumber = null;
        
//        if ( !user.isBusinessAccount() ) {
//            beforePendingNumber = calculatePendingNumber(user);
//        }
        
        request = new Request();
        request.setSent(false);
        request.setReceived(false);
        request.setAd(ad);
        request.setUser(user);
        request.setStatus(RequestStatus.PENDING);
        Long requestId = requestDao.save(request);
        
        ad.setStatus(AdStatus.IN_PROGRESS);
        
        if ( !user.isBusinessAccount() ) {
//            BigDecimal currentPendingNumber = calculatePendingNumber(user);
//            BigDecimal pendingDelta = currentPendingNumber.subtract(beforePendingNumber);

            UserTransaction transaction = new UserTransaction(request);
            transaction.setUser(user);
            transaction.setUserPoint(user.getUserPoint());
//            transaction.setPendingNumber(currentPendingNumber);
//            transaction.setPendingScore(pendingDelta.signum() > 0 ? pendingDelta : BigDecimal.ZERO);
            userTransactionDao.save(transaction);
        }
        
        if ( text != null && !text.trim().isEmpty() ) {
            Message message = new Message(text);
            message.setTo(creator);
            message.setFrom(user);
            message.setRequest(request);
            messageDao.save(message);
        }
        
        try {
            //NOTE: in case of request existing bookmark should be removed
            removeBookmark(adId);
        } catch ( BookmarkNotFoundException ex ) {
        }
        
        if ( ad.getType() == AdType.BUSINESS ) {
            //auto select request for business ads
            selectRequest(request, ad.getCreator());
        }
        
        Map<String, Object> vars = new HashMap<String, Object>(0);
        vars.put("ad", ad);
        vars.put("request", request);
        
        emailSender.sendNotification(NotificationType.AD_REQUESTED, creator, vars);
        
        return requestId;
    }
    
    @Override
    @Transactional
    public void cancelRequest(Long requestId) throws RequestNotFoundException, InvalidRequestException, InvalidAdStateException {
        User currentUser = getCurrentUser();
        Request request = validateRequest(requestId);
        Ad ad = request.getAd();
        UserTransaction transaction;
        
        if ( ad.getStatus() == AdStatus.IN_PROGRESS ) {
            //continue
        } else {
            throw new InvalidAdStateException("Request can't be cancelled as its ad state (" + ad.getStatus() + ") is not as expected!");
        }
        
        if ( ad.getCreator().equals(currentUser) ) {
            //the ad creator cancelling the request
            transaction = userTransactionDao.getByRequest(request.getUser().getId(), requestId);
        } else if ( request.getUser().equals(currentUser) ) {
            //the requestor cancelling the request
            transaction = userTransactionDao.getByRequest(currentUser.getId(), requestId);
        } else {
            throw new InvalidRequestException("Only owned requests can be cancelled");
        }
        
        if ( transaction == null ) {
            throw new InvalidRequestException("No associated transaction for the request (requestId: " + requestId + ")");
        }
        
        if ( ad.getCreator().equals(currentUser) ) {
            if ( request.isDeclined() ) {
                throw new InvalidRequestException("Request (requestId: " + requestId + ") is already canceled (declined)");
            }
            
            //canceling (declining) by the ad owner
            ad.decline(request);
        } else {
            if ( ad.getType() == AdType.MEMBER && request.isAccepted() ) {
                //member typed ads accepted request cannot be cancelled
                throw new InvalidRequestException("Request (requestId: " + requestId + ") is already accepted/selected, canceling not allowed.");
            }
            
            //canceling by the requestor
            ad.cancel(request);
        }
        
        transaction.markAsFinalized();
        userTransactionDao.update(transaction);
        
        if ( ad.getCreator().equals(currentUser) ) {
            Map<String, Object> vars = new HashMap<String, Object>(0);
            vars.put("user", request.getUser());
            vars.put("ad", ad);
            
            emailSender.sendNotification(NotificationType.REQUEST_DECLINED, request.getUser(), vars);
        } else {
            Map<String, Object> vars = new HashMap<String, Object>(0);
            vars.put("user", ad.getCreator());
            vars.put("ad", ad);
            
            emailSender.sendNotification(NotificationType.REQUEST_CANCELED, ad.getCreator(), vars);
        }
    }
    
    @Override
    @Transactional
    public void selectRequest(Long requestId) throws RequestNotFoundException, InvalidRequestException, InvalidAdStateException {
        Request request = validateRequest(requestId);
        User user = getCurrentUser();
        
        selectRequest(request, user);
    }
    
    @Override
    @Transactional
    public RequestDto getRequestById(Long requestId) throws RequestNotFoundException {
        Request request = validateRequest(requestId);
        return new RequestDto(request);
    }
    
    @Override
    @Transactional
    public List<RequestDto> getRequests(Long adId) throws AdNotFoundException {
        Ad ad = validateAd(adId);
        List<Request> requests = requestDao.getByAd(adId);
        List<RequestDto> result = new LinkedList<RequestDto>();
        
        if ( requests != null && !requests.isEmpty() ) {
            for ( Request request : requests ) {
                result.add(new RequestDto(request));
            }
        }
        return result;
    }
    
    @Override
    @Transactional
    public List<RequestDto> getRequestsByUser(Long userId) throws UserNotFoundException {
        User user = validateUser(userId);
        List<Request> requests = getActiveRequestsByUser(userId, true);
        List<RequestDto> result = new LinkedList<RequestDto>();
        
        for ( Request request : requests ) {
            result.add(new RequestDto(request));
        }
        
        return result;
    }
    
    @Override
    @Transactional
    public List<RequestDto> getRequestsForUserWithoutRating(Long userId) throws UserNotFoundException {
        User user = validateUser(userId);
        List<Request> requests = requestDao.getForUser(userId);
        List<RequestDto> result = new LinkedList<RequestDto>();
        
        if ( requests != null && !requests.isEmpty() ) {
            for ( Request request : requests ) {
                if ( !request.isAccepted() ) {
                    continue;
                }
                
                Long fromUserId = request.getUser().getId();
                Long adId = request.getAd().getId();
                Rating rating = ratingDao.get(fromUserId, adId);
                if ( rating == null ) {
                    result.add(new RequestDto(request));
                }
            }
        }
        return result;
    }
    
    @Override
    @Transactional
    public void markAsSent(Long requestId) throws RequestNotFoundException, InvalidRequestException {
        User user = getCurrentUser();
        Request request = validateRequest(requestId);
        markAsSent(user, request);
        
        Map<String, Object> vars = new HashMap<String, Object>(0);
        vars.put("user", request.getUser());
        vars.put("request", request);
        
        emailSender.sendNotification(NotificationType.REQUEST_SENT, request.getUser(), vars);
    }
    
    @Override
    @Transactional
    public void markAsReceived(Long requestId) throws RequestNotFoundException, InvalidRequestException {
        Request request = validateRequest(requestId);
        User user = getCurrentUser();
        Ad ad = request.getAd();
        UserTransaction transaction = userTransactionDao.getByRequest(user.getId(), requestId);
        
        if ( !request.getUser().equals(user) ) {
            throw new InvalidRequestException("Only requestor can mark as received.");
        }
        if ( !request.isAccepted()) {
            throw new InvalidRequestException("Cannot mark request (requestId: " + requestId + ") as received as it is not selected/accepted.");
        }
        if ( transaction == null ) {
            throw new InvalidRequestException("No associated transaction for the request (requestId: " + requestId + ")");
        }
        
        if ( request.getStatus() == RequestStatus.ACCEPTED ) {
            //marking automatically as sent if the status remained as ACCEPTED (if the SENT was skipped)
            User owner = ad.getCreator();
            markAsSent(owner, request);
        }
        
        if ( !request.isSent() ) {
            throw new InvalidRequestException("Cannot mark request (requestId: " + requestId + ") as received as it was not yet sent.");
        }
        
        request.markAsReceived();
        
        if ( ad.getAdData().getQuantity() == 0 ) {
            //ending ad if there are no more items
            ad.markAsSold();
            //ad.setExpired(true);
        }
        
        transaction.markAsFinalized();
        userTransactionDao.update(transaction);
        
        UserPoint userPoint = user.getUserPoint();
        userPoint.addGivingNumber(transaction.getPendingGivingNumber());
        userPoint.addReceivingNumber(transaction.getPendingReceivingNumber());
//        userPoint.removeNumber(transaction.getPendingNumber());
//        userPoint.removeScore(transaction.getPendingScore());
        userPointDao.update(userPoint);
        
        Map<String, Object> vars = new HashMap<String, Object>(0);
        vars.put("user", request.getUser());
        vars.put("request", request);
        
        emailSender.sendNotification(NotificationType.REQUEST_RECEIVED, ad.getCreator(), vars);
    }
    
    
    
    //***************
    //* ad bookmark *
    //***************

    @Override
    @Transactional
    public Long bookmarkAd(Long adId) throws AdNotFoundException, GeneralException {
        Long currentUserId = getCurrentUserId();
        Bookmark bookmark = bookmarkDao.get(currentUserId, adId);

        if (bookmark == null) {
            User currentUser = getCurrentUser();
            Ad ad = validateAd(adId);
            
            if ( ad.getCreator().equals(currentUser) ) {
                throw new GeneralException("Cannot bookmark owned ads.");
            }
            
            bookmark = new Bookmark(currentUser, ad);
            bookmarkDao.save(bookmark);
        }

        return bookmark.getId();
    }

    @Override
    public void removeBookmark(Long adId) throws BookmarkNotFoundException {
        Long currentUserId = getCurrentUserId();
        Bookmark bookmark = bookmarkDao.get(currentUserId, adId);

        if (bookmark == null) {
            throw new BookmarkNotFoundException(adId);
        }

        bookmarkDao.delete(bookmark);
    }
    
    @Override
    @Transactional
    public List<AdDto> getBookmarkedAds() {
        try {
            return getBookmarkedAds(getCurrentUserId());
        } catch ( UserNotFoundException ex ) {
            logger.error("User (current/logged) not found", ex);
            return new ArrayList<AdDto>();
        }
    }
    
    @Override
    @Transactional
    public List<AdDto> getBookmarkedAds(Long userId) throws UserNotFoundException {
        User user = validateUser(userId);
        List<Ad> bookmarkedAds = bookmarkDao.getBookmarkedAds(user);
        List<AdDto> result = new LinkedList<AdDto>();

        if ( bookmarkedAds != null && !bookmarkedAds.isEmpty() ) {
            for (Ad ad : bookmarkedAds) {
                AdDto adDto = new AdDtoBuilder(ad)
                        .setCurrentUser(user)
                        .includeCreator(true) //TODO: maybe this is not needed
                        .includeCanRequest()
                        .build();
                adDto.setInBookmarks(true);
                adDto.setRequested(ad.isRequested(user, false));
                result.add(adDto);
            }
        }
        return result;
    }

    
    
    //****************
    //* ad spam mark *
    //****************
    
    @Override
    @Transactional
    public void markAsSpam(Long adId) throws AdNotFoundException {
        Ad ad = validateAd(adId);
        User currentUser = getCurrentUser();
        
        // Don't allow to mark twice as the same user
        for (SpamMark mark : ad.getSpamMarks()) {
            if (mark.getWitness().equals(currentUser)) {
                return;
            }
        }

        if (ad.getSpamMarks().size() + 1 >= Constants.SPAMMARK_MAX_ALLOWED) {
            ad.markAsSpam();
            //ad.markAsDeleted();
        }

        SpamMark newMark = new SpamMark(ad, currentUser);
        spamMarkDao.save(newMark);
    }

    @Override
    @Transactional
    public void unmarkAsSpam(Long adId) throws AdNotFoundException {
        Ad ad = validateAd(adId);
        User currentUser = getCurrentUser();
        SpamMark markToRemove = null;

        for (SpamMark mark : ad.getSpamMarks()) {
            if (mark.getWitness().equals(currentUser)) {
                markToRemove = mark;
                break;
            }
        }

        if (markToRemove != null) {
            ad.getSpamMarks().remove(markToRemove);

            // restore ad
            if (ad.getSpamMarks().size() < Constants.SPAMMARK_MAX_ALLOWED) {
                //ad.unmarkAsDeleted();
                ad.unmarkAsSpam();
            }

            spamMarkDao.delete(markToRemove);
        }
    }
    
    
    
    //*************
    //* ad rating *
    //*************
    
    @Override
    @Transactional
    public float rateAd(RatingDto ratingDto) throws AdNotFoundException, UserNotFoundException, InvalidRateOperationException, AlreadyRatedException, InvalidAdStateException {
        Ad ad = validateAd(ratingDto.getAdId());
        User from = getCurrentUser();
        User to = validateUser(ratingDto.getToUserId());
        String text = ratingDto.getText();
        int ratingValue = ratingDto.getValue();
        
        if (ratingValue != -1 && ratingValue != 1) {
            throw new InvalidRateOperationException("Only '1' or '-1' can be used as rating values!");
        }
        if ( from.equals(to) ) {
            throw new InvalidRateOperationException("You cannot rate your ad for yourself.");
        }
        if ( !ad.getCreator().equals(from) && !ad.getCreator().equals(to) ) {
            throw new InvalidRateOperationException("Invalid user to rate.");
        }

        for (Rating r : ad.getRatings()) {
            if (r.getFrom().equals(from)) {
                throw new AlreadyRatedException("You've already rated this ad!");
            }
        }
        
        Long adId = ad.getId();
        Long userId;
        if (ad.getCreator().equals(to)) {
            userId = from.getId();
        } else {
            userId = to.getId();
        }

        Request request = requestDao.get(userId, adId);
        if ( request == null ) {
            throw new InvalidRateOperationException("There is no request for user (userId: " + userId + ") on this ad (adId: " + adId + ")");
        }
        if ( !request.isAccepted() ) {
            throw new InvalidRateOperationException("The request is not selected on this ad (adId: " + adId + ")");
        }
        if ( !request.isSent() && !request.isReceived() ) {
            throw new InvalidRateOperationException("The request is not sent or received on this ad (adId: " + adId + ")");
        }

        Rating rating = new Rating(ad, from, to, text, ratingValue);
        ratingDao.save(rating);
        
        ad.getRatings().add(rating);

        // recalculate ad rating
        float totalRating = 0.0f;
        for (Rating r : ad.getRatings()) {
            totalRating += r.getValue();
        }

        ad.setRating(totalRating);
        return totalRating;
    }
    
    @Override
    @Transactional
    public List<RatingDto> getReceivedRatings(Long userId) throws UserNotFoundException {
        User user = validateUser(userId);
        List<Rating> ratings = ratingDao.getReceivedForUser(user.getId());
        List<RatingDto> result = new LinkedList<RatingDto>();
        
        if ( ratings != null && !ratings.isEmpty() ) {
            for ( Rating rating : ratings ) {
                result.add(new RatingDto(rating));
            }
        }
        return result;
    }
    
    @Override
    @Transactional
    public List<RatingDto> getSentRatings(Long userId) throws UserNotFoundException {
        User user = validateUser(userId);
        List<Rating> ratings = ratingDao.getSentByUser(user.getId());
        List<RatingDto> result = new LinkedList<RatingDto>();
        
        if ( ratings != null && !ratings.isEmpty() ) {
            for ( Rating rating : ratings ) {
                result.add(new RatingDto(rating));
            }
        }
        return result;
    }

    
    
    // internal helpers
    
    private Image validateImage(Long imageId) throws ImageNotFoundException {
        Image image = null;
        try {
            image = imageDao.get(imageId);
        } catch ( IOException ex ) {
            logger.error("Could not load image (imageId: " + imageId + ")", ex);
            throw new ImageNotFoundException(imageId);
        }
        if (image == null) {
            throw new ImageNotFoundException(imageId);
        }
        return image;
    }
    
    private Category validateCategory(Long categoryId) throws AdValidationException {
        if (categoryId == null) {
            throw new AdValidationException(AdField.CATEGORY_ID, "Category id not specified!");
        }

        Category category = categoryDao.get(categoryId);
        if (category == null) {
            throw new AdValidationException(AdField.CATEGORY_ID, "Category with id = " + categoryId
                    + " not found!");
        }
        return category;
    }
    
    
    
    private Image saveImage(ImageDto imageDto, AdField field) throws AdValidationException {
        if (imageDto != null) {
            if ( !imageDto.isValid() ) {
                throw new AdValidationException(field, "Invalid image specified!");
            }
            
            try {
                Image image = imageDto.toImage();
                imageDao.save(image);
                return image;
            } catch ( IOException ex ) {
                logger.error("Exception when saving image", ex);
                throw new AdValidationException(field, "Could not save image!");
            }
        }
        return null;
    }
    
    
    
    private List<CategoryDto> getCategoriesInternal(Long categoryId, boolean includeSubcategories) {
        List<CategoryDto> result = new LinkedList<CategoryDto>();
        List<Category> categories = categoryDao.getSubcategories(categoryId);

        for (Category category : categories) {
            CategoryDto categoryDto = new CategoryDto(category, includeSubcategories);
            result.add(categoryDto);
        }

        return result;
    }
    
    private boolean includeOwnedAd(Ad ad, User user, FilterDto filter) {
        if ( filter == null ) {
            return true;
        }
        Boolean includeOwned = filter.getIncludeOwned() != null ? filter.getIncludeOwned() : true;
        if ( !includeOwned && ad.getCreator().equals(user) ) {
            //skipping owned ads
            return false;
        }
        return true;
    }
    
    private boolean inBookmarks(List<Ad> bokmarkedAds, Ad ad) {
        return bokmarkedAds.contains(ad);
    }
    
    private boolean inBookmarks(User user, Ad ad) {
        Bookmark bookmark = bookmarkDao.get(user.getId(), ad.getId());
        if (bookmark != null) {
            return true;
        }
        return false;
    }
    
    /**
     * Returns all the visible requests for the specified user. The 2nd
     * parameter should be used to explicitly set the active status. If is set
     * to false visible (not hidden and not deleted) requests will be included.
     * 
     * @param user
     * @return 
     */
    private List<Request> getActiveRequestsByUser(Long userId, boolean includeOnlyActiveRequests) {
        List<Request> requests = requestDao.getByUser(userId);
        List<Request> result = new LinkedList<Request>();
        
        if ( requests != null && !requests.isEmpty() ) {
            for ( Request request : requests ) {
                if ( includeOnlyActiveRequests ) {
                    if ( request.isActive() ) {
                        result.add(request);
                    }
                } else {
                    if ( request.isVisible()) {
                        result.add(request);
                    }
                }
            }
        }
        return result;
    }
    
    
    private void selectRequest(Request request, User selector) throws InvalidRequestException, InvalidAdStateException {
        Ad ad = request.getAd();
        
        if ( ad.getStatus() == AdStatus.IN_PROGRESS ) {
            //continue
        } else {
            //ad must be IN_PROGRESS to allow request selection
            throw new InvalidAdStateException("Ad can't be selected as its state (" + ad.getStatus() + ") is not as expected!");
        }
        if ( !ad.getCreator().equals(selector) ) {
            throw new InvalidRequestException("Only owner can select a request!");
        }
        if ( request.isAccepted()) {
            throw new InvalidRequestException("Request already selected");
        }
        if ( ad.getAdData().getQuantity() <= 0 ) {
            throw new InvalidRequestException("No more available.");
        }
        
        ad.select(request);
        //ad.setExpiresAt(new Date()); //reset (???) the expiration date
        
        Map<String, Object> vars = new HashMap<String, Object>(0);
        vars.put("user", request.getUser());
        vars.put("request", request);
        
        emailSender.sendNotification(NotificationType.REQUEST_ACCEPTED, request.getUser(), vars);
    }
    
    private void markAsSent(User user, Request request) throws RequestNotFoundException, InvalidRequestException {
        Ad ad = request.getAd();
        Long adId = ad.getId();
        UserTransaction transaction = userTransactionDao.getByAd(user.getId(), adId);
        
        if ( !ad.getCreator().equals(user) ) {
            throw new InvalidRequestException("Only creator users can mark as sent.");
        }
        if ( !request.isAccepted() ) {
            throw new InvalidRequestException("The request (requestId: " + request.getId() + ") is not accepted, cannot mark as sent.");
        }
        if ( transaction == null ) {
            throw new InvalidRequestException("No associated transaction for the ad (adId: " + adId + ")");
        }
        if ( request.getStatus() != RequestStatus.ACCEPTED ) {
            throw new InvalidRequestException("The request (requestId: " + request.getId() + ") is not in accepted state, cannot mark as sent.");
        }
        
        request.markAsSent();
        
        transaction.markAsFinalized();
        userTransactionDao.update(transaction);
        
        UserPoint userPoint = user.getUserPoint();
        userPoint.addGivingNumber(transaction.getPendingGivingNumber());
        userPoint.addReceivingNumber(transaction.getPendingReceivingNumber());
//        userPoint.addNumber(transaction.getPendingNumber());
//        userPoint.addScore(transaction.getPendingScore());
        userPointDao.update(userPoint);
    }
    
    
    
//    private BigDecimal calculatePendingNumber(User user) {
//        BigDecimal number = UserPoint.getGenerosityNumber(user, false);
//        BigDecimal pendingNumber = UserPoint.getGenerosityNumber(user, true).subtract(number);
//        return pendingNumber;
//    }
}
