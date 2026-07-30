import SwiftUI

struct QueueView: View {
    @EnvironmentObject private var model: AppModel
    @State private var addToPlaylistItem: MAMediaItem?

    var body: some View {
        List {
            if let current = model.activeQueue?.currentItem {
                Section("Playing") {
                    queueRow(current, isCurrent: true)
                        .contextMenu {
                            queueContextActions(for: current, isCurrent: true)
                        }
                }
            }

            Section("Up Next") {
                ForEach(upNextItems) { item in
                    queueRow(item, isCurrent: false)
                        .contextMenu {
                            queueContextActions(for: item, isCurrent: false)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                model.removeQueueItem(item)
                            } label: {
                                Label("Remove", systemImage: "trash")
                            }
                        }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Queue")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(role: .destructive) {
                    model.clearQueue()
                } label: {
                    Image(systemName: "trash")
                }
                .disabled(model.queueItems.isEmpty)
                .accessibilityLabel("Clear queue")
            }
        }
        .overlay {
            if model.queueItems.isEmpty && model.activeQueue?.currentItem == nil {
                ContentUnavailableView(
                    "Queue Is Empty",
                    systemImage: "text.line.first.and.arrowtriangle.forward",
                    description: Text("Add music from the Library tab.")
                )
            }
        }
        .refreshable {
            await model.refresh()
        }
        .sheet(item: $addToPlaylistItem) { item in
            AddToPlaylistSheet(item: item)
        }
    }

    private var upNextItems: [MAQueueItem] {
        let sortedItems = model.queueItems.sorted { lhs, rhs in
            switch (lhs.index, rhs.index) {
            case let (left?, right?):
                return left < right
            case (_?, nil):
                return true
            case (nil, _?):
                return false
            case (nil, nil):
                return false
            }
        }

        guard let queue = model.activeQueue else {
            return sortedItems
        }

        let currentIndex = queue.currentIndex ?? queue.currentItem?.index

        if let currentIndex {
            return sortedItems.filter { item in
                guard let itemIndex = item.index else {
                    return item.queueItemID != queue.currentItem?.queueItemID
                }
                return itemIndex > currentIndex
            }
        }

        guard let currentID = queue.currentItem?.queueItemID else {
            return sortedItems
        }

        return sortedItems.filter { $0.queueItemID != currentID }
    }

    private func queueRow(_ item: MAQueueItem, isCurrent: Bool) -> some View {
        Button {
            model.playQueueItem(item)
        } label: {
            HStack(spacing: 12) {
                ArtworkView(url: model.imageURL(for: item, size: 160), cornerRadius: 5)
                    .overlay {
                        if isCurrent {
                            Image(systemName: "waveform")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(6)
                                .background(AppTheme.accent.opacity(0.92), in: Circle())
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                                .padding(4)
                        }
                    }
                .frame(width: 48, height: 48)

                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title)
                        .font(.body.weight(isCurrent ? .semibold : .regular))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if !item.subtitle.isEmpty {
                        Text(item.subtitle)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }

                Spacer()

                if let duration = item.duration {
                    Text(duration.formattedDuration)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func queueContextActions(for item: MAQueueItem, isCurrent: Bool) -> some View {
        Button {
            model.playQueueItem(item)
        } label: {
            Label(isCurrent ? "Restart" : "Play Now", systemImage: "play.fill")
        }

        if let mediaItem = item.mediaItem {
            Divider()

            MediaContextActions(item: mediaItem, includePlayback: false) {
                addToPlaylistItem = mediaItem
            }
        }

        if !isCurrent {
            Divider()

            Button(role: .destructive) {
                model.removeQueueItem(item)
            } label: {
                Label("Remove from Queue", systemImage: "trash")
            }
        }
    }
}
