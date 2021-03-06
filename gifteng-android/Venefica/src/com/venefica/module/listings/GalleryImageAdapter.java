package com.venefica.module.listings;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;

import com.venefica.module.listings.post.PostListingActivity;
import com.venefica.module.main.R;
import com.venefica.services.ImageDto;
import com.venefica.utils.Constants;
import com.venefica.utils.VeneficaApplication;

/**
 * @author avinash 
 * Adapter class for image gallery
 */
public class GalleryImageAdapter extends BaseAdapter {
	/**
	 * @author avinash
	 * listener to show hide action mode in fragments
	 */
	public interface OnActionModeListener{
		public void showActionMode(boolean show);
		public void setActionModeTitle(String title);
	}
	private OnActionModeListener actionModeListener;
	private Context context;

	private static ImageView imageView;

	private List<ImageDto> plotsImages;
	private List<Bitmap> images;
	private boolean useDrawables;
	private static ViewHolder holder;
	private boolean showThumbnails;
	private boolean useActionModes;

	private int coverPosition = -1;
	private ArrayList<Integer> selectedPositions;
	private ArrayList<Long> deletePositions;

	public GalleryImageAdapter(Context context, List<ImageDto> plotsImages, List<Bitmap> images,
			boolean useDrawables, boolean showThumbnails, boolean useActionModes) {

		this.context = context;
		this.plotsImages = plotsImages;
		this.images = images;
		this.useDrawables = useDrawables;
		this.showThumbnails = showThumbnails;
		this.useActionModes = useActionModes;
		this.selectedPositions = new ArrayList<Integer>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getCount()
	 */
	public int getCount() {
		if (useDrawables && this.plotsImages != null && this.images != null) {
			return (this.plotsImages.size() + this.images.size());
		} else if (this.plotsImages != null){
			return this.plotsImages.size();
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getItem(int)
	 */
	public Object getItem(int arg0) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getItemId(int)
	 */
	public long getItemId(int position) {
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.widget.Adapter#getView(int, android.view.View,
	 * android.view.ViewGroup)
	 */
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {

			holder = new ViewHolder();

			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.view_postlisting_image, null);
			if (showThumbnails) {
				holder.imageView = (ImageView) convertView.findViewById(R.id.imgPostListingSwitcherImage);				
			} else {
				holder.imageView = (ImageView) convertView.findViewById(R.id.imgListingDetailSwitcherImage);
			}
			holder.imageView.setVisibility(View.VISIBLE);
			holder.txtCoverMark = (ImageView) convertView.findViewById(R.id.txtPostListingCoverTicker);
			holder.chkSelected = (CheckBox) convertView.findViewById(R.id.chkPostListingImageSelect);
			holder.chkSelected.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					//handle action mode visibility and message selection 
					Integer id = Integer.parseInt(buttonView.getContentDescription().toString());
					if (buttonView.isChecked() && !selectedPositions.contains(id)) {
						selectedPositions.add(id);
						if (selectedPositions.size() >= 1) {
							actionModeListener.showActionMode(true);
						}
					} else if (!buttonView.isChecked() && selectedPositions.contains(id)) {
						selectedPositions.remove(id);
						selectedPositions.trimToSize();
						if (selectedPositions.size() == 0) {
							actionModeListener.showActionMode(false);
						} else if (selectedPositions.size() == 1) {
							actionModeListener.showActionMode(true);
						}
					}
					//set action mode title
					actionModeListener.setActionModeTitle(selectedPositions.size() 
							+" "+ context.getResources().getString(R.string.label_selected));
				}
			});
			convertView.setTag(holder);

		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		if (useActionModes) {
			holder.chkSelected.setVisibility(View.VISIBLE);
			holder.chkSelected.setContentDescription(position+"");
			if (selectedPositions.contains(Integer.parseInt(holder.chkSelected.getContentDescription().toString()))) {
				holder.chkSelected.setChecked(true);
			}
		} else {
			holder.chkSelected.setVisibility(View.INVISIBLE);
		}
		if (useDrawables) {
			if (showThumbnails && position >= plotsImages.size()) {
				holder.imageView.setImageBitmap(images.get(position - plotsImages.size()));
			} else if (this.context instanceof PostListingActivity){
				((VeneficaApplication) ((PostListingActivity)this.context).getApplication())
				.getImgManager().loadImage(plotsImages.get(position) != null 
				? Constants.PHOTO_URL_PREFIX + plotsImages.get(position).getUrl():"", 
						holder.imageView, this.context.getResources().getDrawable(R.drawable.icon_picture_white));
			}	
		} else {			
			if(this.context instanceof ListingDetailsActivity){
				((VeneficaApplication) ((ListingDetailsActivity)this.context).getApplication())
				.getImgManager().loadImage(plotsImages.get(position) != null 
				? Constants.PHOTO_URL_PREFIX + plotsImages.get(position).getUrl():"", 
						holder.imageView, this.context.getResources().getDrawable(R.drawable.icon_picture_white));
			} 		
		}	
		if (coverPosition == position) {
			holder.txtCoverMark.setVisibility(View.VISIBLE);
			holder.txtCoverMark.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_monitor));
		} else if (deletePositions!= null && position < plotsImages.size()
				&& deletePositions.contains(plotsImages.get(position).getId())) {
			holder.txtCoverMark.setVisibility(View.VISIBLE);
			holder.txtCoverMark.setImageDrawable(context.getResources().getDrawable(R.drawable.icon_trash));
		}else {
			holder.txtCoverMark.setVisibility(View.GONE);
		}
				
		return convertView;
	}

	private static class ViewHolder {
		ImageView txtCoverMark;
		ImageView imageView;
		CheckBox chkSelected;
	}
	
	/**
	 *  set cover image position for post layout gallery
	 * @param coverPosition
	 */
	public void setCoverImagePosition(int coverPosition){
		this.coverPosition  = coverPosition;
	}
	/**
	 * Reset cover image position for post layout gallery
	 */
	public void resetCoverImagePosition(){
		this.coverPosition  = -1;
	}

	/**
	 * @return the selectedPositions
	 */
	public ArrayList<Integer> getSelectedPositions() {
		return selectedPositions;
	}

	/**
	 * @param selectedPositions the selectedPositions to set
	 */
	public void setSelectedPositions(ArrayList<Integer> selectedPositions) {
		this.selectedPositions = selectedPositions;
	}
	/**
	 * clear selection
	 */
	public void clearSelectedPositions(){
		this.selectedPositions.clear();
	}

	/**
	 * @return the actionModeListener
	 */
	public OnActionModeListener getActionModeListener() {
		return actionModeListener;
	}

	/**
	 * @param actionModeListener the actionModeListener to set
	 */
	public void setActionModeListener(OnActionModeListener actionModeListener) {
		this.actionModeListener = actionModeListener;
	}

	/**
	 * @return the deletePositions
	 */
	public ArrayList<Long> getDeletePositions() {
		return deletePositions;
	}

	/**
	 * @param deletePositions the deletePositions to set
	 */
	public void setDeletePositions(ArrayList<Long> deletePositions) {
		this.deletePositions = deletePositions;
	}
}
