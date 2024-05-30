package ml.docilealligator.infinityforreddit.customtheme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.ListenableFuturePagingSource;
import androidx.paging.PagingState;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.apis.OnlineCustomThemeAPI;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;

public class OnlineCustomThemePagingSource extends ListenableFuturePagingSource<String, CustomTheme> {
    private final Executor executor;
    private final OnlineCustomThemeAPI api;
    private final RedditDataRoomDatabase redditDataRoomDatabase;

    public OnlineCustomThemePagingSource(Executor executor, Retrofit onlineCustomThemesRetrofit, RedditDataRoomDatabase redditDataRoomDatabase) {
        this.executor = executor;
        this.redditDataRoomDatabase = redditDataRoomDatabase;
        api = onlineCustomThemesRetrofit.create(OnlineCustomThemeAPI.class);
    }

    @Nullable
    @Override
    public String getRefreshKey(@NonNull PagingState<String, CustomTheme> pagingState) {
        return null;
    }

    @NonNull
    @Override
    public ListenableFuture<LoadResult<String, CustomTheme>> loadFuture(@NonNull LoadParams<String> loadParams) {
        ListenableFuture<Response<String>> customThemes;
        customThemes = api.getCustomThemesListenableFuture(loadParams.getKey());

        ListenableFuture<LoadResult<String, CustomTheme>> pageFuture = Futures.transform(customThemes, this::transformData, executor);

        ListenableFuture<LoadResult<String, CustomTheme>> partialLoadResultFuture =
                Futures.catching(pageFuture, HttpException.class,
                        LoadResult.Error::new, executor);

        return Futures.catching(partialLoadResultFuture,
                IOException.class, LoadResult.Error::new, executor);
    }

    public LoadResult<String, CustomTheme> transformData(Response<String> response) {
        if (response.isSuccessful()) {
            List<CustomTheme> themes = new ArrayList<>();
            try {
                String responseString = response.body();
                JSONObject data = new JSONObject(responseString);
                int page = data.getInt(JSONUtils.PAGE_KEY);
                JSONArray themesArray = data.getJSONArray(JSONUtils.DATA_KEY);
                for (int i = 0; i < themesArray.length(); i++) {
                    try {
                        themes.add(CustomTheme.fromJson(themesArray.getJSONObject(i).getString(JSONUtils.DATA_KEY)));
                    } catch (JSONException ignore) {}
                }

                if (themes.isEmpty()) {
                    return new LoadResult.Page<>(themes, null, null);
                } else {
                    return new LoadResult.Page<>(themes, null, Integer.toString(page + 1));
                }
            } catch (JSONException e) {
                return new LoadResult.Error<>(new Exception("Response failed"));
            }
        } else {
            return new LoadResult.Error<>(new Exception("Response failed"));
        }
    }
}
